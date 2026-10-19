package com.weather.service;

import com.alibaba.fastjson2.JSONObject;
import com.weather.entity.WeatherData;
import com.weather.entity.WeatherWarning;
import com.weather.util.WeatherApiUtil;
import com.weather.util.WeatherCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 城市天气卡片服务：把"实时天气 + 当日最高/最低 + 预警 + 场景映射"打包成
 * Aether 城市卡可直接消费的数据结构。
 *
 * 说明：本服务为纯新增，依赖已有的 WeatherDataService / WeatherWarningService /
 * WeatherApiUtil，未修改任何既有业务逻辑。
 */
@Service
public class WeatherCardService {

    /** 内置城市库（与 Aether 页面 30 城 + AetherController 一致） */
    private static final Map<String, String> CITY_CODES = new LinkedHashMap<>();

    static {
        CITY_CODES.put("北京", "101010100");
        CITY_CODES.put("上海", "101020100");
        CITY_CODES.put("广州", "101280101");
        CITY_CODES.put("深圳", "101280601");
        CITY_CODES.put("成都", "101270101");
        CITY_CODES.put("杭州", "101210101");
        CITY_CODES.put("西安", "101110101");
        CITY_CODES.put("武汉", "101200101");
        CITY_CODES.put("南京", "101190101");
        CITY_CODES.put("重庆", "101040100");
        CITY_CODES.put("天津", "101030100");
        CITY_CODES.put("苏州", "101190401");
        CITY_CODES.put("青岛", "101120201");
        CITY_CODES.put("厦门", "101230201");
        CITY_CODES.put("三亚", "101310201");
        CITY_CODES.put("哈尔滨", "101050101");
        CITY_CODES.put("长春", "101060101");
        CITY_CODES.put("沈阳", "101070101");
        CITY_CODES.put("大连", "101070201");
        CITY_CODES.put("昆明", "101290101");
        CITY_CODES.put("丽江", "101291401");
        CITY_CODES.put("桂林", "101300501");
        CITY_CODES.put("拉萨", "101140101");
        CITY_CODES.put("乌鲁木齐", "101130101");
        CITY_CODES.put("呼和浩特", "101080101");
        CITY_CODES.put("长沙", "101250101");
        CITY_CODES.put("郑州", "101180101");
        CITY_CODES.put("济南", "101120101");
        CITY_CODES.put("合肥", "101220101");
        CITY_CODES.put("香港", "101320101");
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private WeatherDataService weatherDataService;

    @Autowired
    private WeatherWarningService weatherWarningService;

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private WeatherCache cache;

    private static final Duration CARD_TTL = Duration.ofMinutes(10);

    /**
     * 装配一张城市卡所需的数据。
     *
     * @param city 城市中文名
     * @return 卡数据（city / code / temp / text / scene / hi / lo / humidity / windSpeed / windDir / warning / updatedAt），
     *         拉取失败返回 null
     */
    public Map<String, Object> loadCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            return null;
        }
        String key = "card:" + city.trim();
        return cache.getOrLoad(key, Map.class, CARD_TTL, () -> assembleCityCard(city.trim()));
    }

    private Map<String, Object> assembleCityCard(String city) {
        try {
            String code = CITY_CODES.get(city);
            WeatherData now = (code == null) ? null : weatherDataService.getRealtimeWeather(code, city);

            if (now == null) {
                String resolved = searchCityCode(city);
                if (resolved != null) {
                    code = resolved;
                    now = weatherDataService.getRealtimeWeather(code, city);
                }
            }
            if (now == null) {
                return null;
            }

            BigDecimal hi = null;
            BigDecimal lo = null;
            try {
                List<WeatherData> forecast = weatherDataService.getForecast7Day(code, city);
                if (forecast != null && !forecast.isEmpty()) {
                    hi = forecast.get(0).getTempMax();
                    lo = forecast.get(0).getTempMin();
                }
            } catch (Exception ignored) {
                // 取不到预报时高低温留空，由前端做降级展示
            }

            LocalDateTime shanghaiTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            int hour = shanghaiTime.getHour();
            boolean night = hour >= 19 || hour < 6;
            String text = now.getWeatherText();

            String warning = null;
            try {
                List<WeatherWarning> warnings = weatherWarningService.getActiveWarnings(code);
                if (warnings != null && !warnings.isEmpty()) {
                    warning = warnings.get(0).getWarningTitle();
                }
            } catch (Exception ignored) {
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("city", city);
            m.put("code", code);
            m.put("temp", now.getTemp() == null ? null : now.getTemp().intValue());
            m.put("text", text == null ? "未知" : text);
            m.put("scene", sceneOf(text, night));
            m.put("night", night);
            m.put("hi", hi == null ? null : hi.intValue());
            m.put("lo", lo == null ? null : lo.intValue());
            m.put("humidity", now.getHumidity());
            m.put("windSpeed", now.getWindSpeed() == null ? null : now.getWindSpeed().setScale(0, BigDecimal.ROUND_HALF_UP));
            m.put("windDir", now.getWindDir());
            m.put("feelsLike", now.getFeelsLike() == null ? null : now.getFeelsLike().intValue());
            m.put("warning", warning);
            m.put("updatedAt", shanghaiTime.format(TIME_FMT));
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把和风天气文本映射到 7 种手绘场景：
     * clear 晴朗 / cloudy 多云 / rain 小雨 / snow 飘雪 / storm 雷阵雨 / fog 薄雾 / night 晴夜
     */
    public String sceneOf(String text, boolean night) {
        if (text == null || text.trim().isEmpty()) {
            return night ? "night" : "cloudy";
        }
        if (text.contains("雪")) return "snow";
        if (text.contains("雷")) return "storm";
        if (text.contains("雨")) return "rain";
        if (text.contains("雾") || text.contains("霾") || text.contains("沙尘") || text.contains("浮尘")) return "fog";
        if (text.contains("阴")) return "cloudy";
        if (text.contains("云")) return "cloudy";
        if (text.contains("晴")) return night ? "night" : "clear";
        return night ? "night" : "cloudy";
    }

    /** 调用和风天气城市搜索接口解析城市编码 */
    private String searchCityCode(String city) {
        try {
            JSONObject result = weatherApiUtil.searchCity(city);
            if (result != null && result.getJSONArray("location") != null
                    && !result.getJSONArray("location").isEmpty()) {
                return result.getJSONArray("location").getJSONObject(0).getString("id");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 返回内置城市库列表（前端默认展示与联想用） */
    public java.util.List<String> getCityList() {
        return new java.util.ArrayList<>(CITY_CODES.keySet());
    }

    /** 内置城市「名称→编码」目录（供助手等城市识别复用） */
    public java.util.Map<String, String> getCityCatalog() {
        return java.util.Collections.unmodifiableMap(CITY_CODES);
    }

    /** 内置城市库数量（方便测试） */
    public int getCityLibSize() {
        return CITY_CODES.size();
    }
}