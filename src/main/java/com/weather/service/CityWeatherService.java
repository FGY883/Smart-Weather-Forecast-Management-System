package com.weather.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weather.entity.WeatherData;
import com.weather.util.WeatherApiUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 城市天气查询能力（供 AI 助手使用）。
 *
 * 识别问题中的城市（优先内置目录子串匹配，未命中时按天气意图用地理编码接口解析），
 * 抓取实时天气 + 未来数日预报，输出紧凑「天气资料」文本，作为助手的检索增强上下文。
 */
@Service
public class CityWeatherService {

    private static final Logger log = LoggerFactory.getLogger(CityWeatherService.class);

    /** 命中即视为“询问天气”的关键词（用于非目录城市的兜底解析触发条件） */
    private static final List<String> WEATHER_KEYWORDS = Arrays.asList(
            "天气", "气温", "温度", "多少度", "几度", "下雨", "降雨", "降水", "会下雨", "下雪",
            "未来", "预报", "冷", "热", "风大", "风力", "穿什么", "穿衣", "湿度", "空气", "晴天", "阴天", "多云"
    );

    /** 解析非目录城市前先剔除的干扰词 */
    private static final List<String> STOPWORDS = Arrays.asList(
            "今天", "明天", "后天", "现在", "目前", "今晚", "未来几天", "未来一周", "怎么样", "怎样", "如何",
            "多少", "吗", "呢", "的", "了", "查询", "查一下", "查下", "看下", "看看", "请问", "会不会",
            "会不会下", "需不需要", "要不要", "天气", "气温", "温度", "预报", "会下雨", "下雨", "下雪",
            "冷", "热", "穿什么", "穿衣", "湿度", "风力", "风大", "空气", "多少度", "几度"
    );

    private static final Pattern CJK_TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}");

    @Autowired
    private WeatherCardService weatherCardService;

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private WeatherDataService weatherDataService;

    /** 识别到的城市：名称 + 城市编码 + 是否来自内置目录。 */
    public static final class CityHit {
        public final String name;
        public final String code;
        public final boolean fromCatalog;
        CityHit(String name, String code, boolean fromCatalog) {
            this.name = name; this.code = code; this.fromCatalog = fromCatalog;
        }
    }

    public boolean hasWeatherKeyword(String q) {
        if (q == null) return false;
        for (String kw : WEATHER_KEYWORDS) {
            if (q.contains(kw)) return true;
        }
        return false;
    }

    /** 从问题中识别城市；目录优先，未命中且有天气意图时用地理编码接口解析。 */
    public CityHit detectCity(String q) {
        if (q == null || q.trim().isEmpty()) return null;

        // 1) 内置目录：取最长匹配的城市名
        Map<String, String> catalog = weatherCardService.getCityCatalog();
        String best = null;
        for (String name : catalog.keySet()) {
            if (q.contains(name) && (best == null || name.length() > best.length())) {
                best = name;
            }
        }
        if (best != null) {
            return new CityHit(best, catalog.get(best), true);
        }

        // 2) 非目录城市：仅在带天气意图时尝试，避免对功能类问题误发外部请求
        if (hasWeatherKeyword(q)) {
            String candidate = extractCityCandidate(q);
            if (candidate != null) {
                try {
                    JSONObject result = weatherApiUtil.searchCity(candidate);
                    JSONArray loc = result != null ? result.getJSONArray("location") : null;
                    if (loc != null && !loc.isEmpty()) {
                        String code = loc.getJSONObject(0).getString("id");
                        String name = loc.getJSONObject(0).getString("name");
                        if (code != null && !code.isEmpty()) {
                            return new CityHit(name != null && !name.isEmpty() ? name : candidate, code, false);
                        }
                    }
                } catch (Exception e) {
                    log.debug("城市地理编码解析失败 candidate={}: {}", candidate, e.getMessage());
                }
            }
        }
        return null;
    }

    /** 去干扰词后取第一个 2-8 字中文串作为候选城市名。 */
    private String extractCityCandidate(String q) {
        String s = q;
        for (String sw : STOPWORDS) {
            s = s.replace(sw, " ");
        }
        Matcher m = CJK_TOKEN.matcher(s);
        while (m.find()) {
            String token = m.group();
            // 排除把“城市/地区/天气”等残词当城市
            if (!token.contains("城市") && !token.contains("地区") && !token.contains("省") && !token.contains("市")) {
                return token;
            }
        }
        return null;
    }

    /** 生成紧凑天气资料文本；实时数据缺失返回 null。 */
    public String buildDigest(CityHit hit) {
        WeatherData rt;
        try {
            rt = weatherDataService.getRealtimeWeather(hit.code, hit.name);
        } catch (Exception e) {
            log.warn("获取实时天气失败 city={}: {}", hit.name, e.getMessage());
            return null;
        }
        if (rt == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(hit.name).append(" · 实时天气");
        LocalDate date = rt.getWeatherDate() != null ? rt.getWeatherDate() : LocalDate.now();
        sb.append("（").append(date).append("）】\n");
        sb.append("天气：").append(nvl(rt.getWeatherText(), "未知"));
        sb.append("，当前温度 ").append(nvl(dec(rt.getTemp()), "—")).append("℃");
        if (rt.getTempMax() != null || rt.getTempMin() != null) {
            sb.append("，今日 ").append(nvl(dec(rt.getTempMin()), "—")).append("~")
              .append(nvl(dec(rt.getTempMax()), "—")).append("℃");
        }
        if (rt.getHumidity() != null) sb.append("，湿度 ").append(rt.getHumidity()).append("%");
        if (rt.getWindDir() != null || rt.getWindSpeed() != null) {
            sb.append("，").append(nvl(rt.getWindDir(), "")).append(" ")
              .append(nvl(dec(rt.getWindSpeed()), "—")).append("km/h");
        }
        if (rt.getPrecip() != null) sb.append("，降水 ").append(dec(rt.getPrecip())).append("mm");
        sb.append("。");

        try {
            List<WeatherData> fc = weatherDataService.getForecast7Day(hit.code, hit.name);
            if (fc != null && !fc.isEmpty()) {
                sb.append("\n【未来几天预报】\n");
                int n = Math.min(5, fc.size());
                for (int i = 0; i < n; i++) {
                    WeatherData d = fc.get(i);
                    if (d == null || d.getWeatherDate() == null) continue;
                    sb.append("- ").append(d.getWeatherDate()).append(" ")
                      .append(nvl(d.getWeatherText(), ""))
                      .append(" ").append(nvl(dec(d.getTempMin()), "—")).append("~")
                      .append(nvl(dec(d.getTempMax()), "—")).append("℃\n");
                }
            }
        } catch (Exception e) {
            log.debug("获取预报失败 city={}: {}", hit.name, e.getMessage());
        }
        return sb.toString().trim();
    }

    /** 当前支持的城市示例（供无法识别城市时提示用户）。 */
    public String supportedCityHint() {
        List<String> names = weatherCardService.getCityList();
        StringBuilder sb = new StringBuilder("目前可直接查询的城市包括：");
        for (int i = 0; i < Math.min(12, names.size()); i++) {
            sb.append(names.get(i));
            if (i < Math.min(12, names.size()) - 1) sb.append("、");
        }
        if (names.size() > 12) sb.append(" 等 ").append(names.size()).append(" 个城市");
        sb.append("；也可以直接说出城市名，我会尝试为你查询。");
        return sb.toString();
    }

    private static String nvl(String s, String d) {
        return (s == null || s.trim().isEmpty()) ? d : s;
    }

    private static String dec(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }
}
