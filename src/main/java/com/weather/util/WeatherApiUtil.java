package com.weather.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weather.entity.WeatherData;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class WeatherApiUtil {

    private static final Logger log = LoggerFactory.getLogger(WeatherApiUtil.class);

    @Value("${weather.api.key}")
    private String apiKey;

    @Value("${weather.api.base-url}")
    private String baseUrl;

    @Value("${weather.api.geo-url}")
    private String geoUrl;

    /**
     * 查询城市信息
     */
    public JSONObject searchCity(String location) {
        try {
            String encodedLocation = URLEncoder.encode(location, "UTF-8");
            String url = geoUrl + "/v2/city/lookup?location=" + encodedLocation + "&key=" + apiKey;
            log.info("搜索城市URL: {}", url);
            String resp = doGet(url);
            log.info("搜索城市响应: {}", resp);
            if (resp != null) {
                JSONObject json = JSONObject.parseObject(resp);
                if ("200".equals(json.getString("code"))) {
                    return json;
                } else {
                    log.warn("城市搜索API返回错误: code={}, message={}", json.getString("code"), json.getString("message"));
                }
            }
        } catch (Exception e) {
            log.error("城市搜索URL编码失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取实时天气
     */
    public WeatherData getRealtimeWeather(String cityCode, String cityName) {
        String url = baseUrl + "/weather/now?location=" + cityCode + "&key=" + apiKey;
        log.info("获取实时天气URL: {}", url);
        String resp = doGet(url);
        log.info("实时天气响应: {}", resp);
        if (resp == null) return null;

        JSONObject json = JSONObject.parseObject(resp);
        if (!"200".equals(json.getString("code"))) {
            log.warn("实时天气API返回错误: code={}, message={}", json.getString("code"), json.getString("message"));
            return null;
        }

        JSONObject now = json.getJSONObject("now");
        WeatherData data = new WeatherData();
        data.setCity(cityName);
        data.setCityCode(cityCode);
        data.setWeatherDate(LocalDate.now());
        data.setWeatherText(now.getString("text"));
        data.setTemp(new BigDecimal(now.getString("temp")));
        data.setFeelsLike(new BigDecimal(now.getString("feelsLike")));
        data.setHumidity(Integer.parseInt(now.getString("humidity")));
        data.setWindDir(now.getString("windDir"));
        data.setWindSpeed(new BigDecimal(now.getString("windSpeed")));
        data.setWindScale(now.getString("windScale"));
        data.setPressure(Integer.parseInt(now.getString("pressure")));
        data.setVisibility(new BigDecimal(now.getString("vis")));
        data.setPrecip(new BigDecimal(now.getString("precip")));
        data.setDataType(1);
        return data;
    }

    /**
     * 获取7天天气预报
     */
    public List<WeatherData> getForecast7Day(String cityCode, String cityName) {
        String url = baseUrl + "/weather/7d?location=" + cityCode + "&key=" + apiKey;
        log.info("获取7天预报URL: {}", url);
        String resp = doGet(url);
        log.info("7天预报响应: {}", resp);
        if (resp == null) return null;

        JSONObject json = JSONObject.parseObject(resp);
        if (!"200".equals(json.getString("code"))) {
            log.warn("7天预报API返回错误: code={}, message={}", json.getString("code"), json.getString("message"));
            return null;
        }

        List<WeatherData> list = new ArrayList<>();
        JSONArray daily = json.getJSONArray("daily");
        for (int i = 0; i < daily.size(); i++) {
            JSONObject d = daily.getJSONObject(i);
            WeatherData data = new WeatherData();
            data.setCity(cityName);
            data.setCityCode(cityCode);
            data.setWeatherDate(LocalDate.parse(d.getString("fxDate")));
            data.setWeatherText(d.getString("textDay"));
            data.setTempMax(new BigDecimal(d.getString("tempMax")));
            data.setTempMin(new BigDecimal(d.getString("tempMin")));
            data.setHumidity(Integer.parseInt(d.getString("humidity")));
            data.setWindDir(d.getString("windDirDay"));
            data.setWindSpeed(new BigDecimal(d.getString("windSpeedDay")));
            data.setWindScale(d.getString("windScaleDay"));
            data.setPrecip(new BigDecimal(d.getString("precip")));
            data.setUvIndex(Integer.parseInt(d.getString("uvIndex")));
            data.setDataType(2);
            list.add(data);
        }
        return list;
    }

    /**
     * 获取历史天气（过去N天）
     */
    public List<WeatherData> getHistoricalWeather(String cityCode, String cityName, int days) {
        List<WeatherData> allHistory = new ArrayList<>();
        // 和风天气历史接口要求 date 为 yyyyMMdd 格式
        DateTimeFormatter fmt = DateTimeFormatter.BASIC_ISO_DATE;
        // 和风天气历史API每次只能查1天，需要循环调用
        for (int i = 1; i <= days; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            String url = baseUrl + "/historical/weather?location=" + cityCode
                    + "&date=" + date.format(fmt) + "&key=" + apiKey;
            log.info("获取历史天气URL: {}", url);
            String resp = doGet(url);
            if (resp == null) continue;

            try {
                JSONObject json = JSONObject.parseObject(resp);
                if (!"200".equals(json.getString("code"))) {
                    log.warn("历史天气API返回错误: date={}, code={}", date, json.getString("code"));
                    continue;
                }

                // 日聚合数据在 weatherDaily 字段（旧代码误用 weather 字段，导致历史数据永远取不到）
                JSONObject daily = json.getJSONObject("weatherDaily");
                if (daily == null) {
                    daily = json.getJSONObject("weather"); // 兼容旧版响应结构
                }
                if (daily == null) continue;

                WeatherData data = new WeatherData();
                data.setCity(cityName);
                data.setCityCode(cityCode);
                data.setWeatherDate(date);
                if (daily.getString("tempMax") != null) {
                    data.setTempMax(new BigDecimal(daily.getString("tempMax")));
                }
                if (daily.getString("tempMin") != null) {
                    data.setTempMin(new BigDecimal(daily.getString("tempMin")));
                }
                if (data.getTempMax() != null && data.getTempMin() != null) {
                    data.setTemp(data.getTempMax().add(data.getTempMin())
                            .divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP));
                }
                if (daily.getString("humidity") != null) {
                    data.setHumidity(Integer.parseInt(daily.getString("humidity")));
                }
                if (daily.getString("precip") != null) {
                    data.setPrecip(new BigDecimal(daily.getString("precip")));
                }
                if (daily.getString("pressure") != null) {
                    data.setPressure(Integer.parseInt(daily.getString("pressure")));
                }

                // weatherDaily 不含天气文本/风向，取当天最接近正午的小时数据补充
                JSONObject noon = pickNoonHour(json.getJSONArray("weatherHourly"));
                if (noon != null) {
                    data.setWeatherText(noon.getString("text"));
                    data.setWindDir(noon.getString("windDir"));
                    if (noon.getString("windSpeed") != null) {
                        data.setWindSpeed(new BigDecimal(noon.getString("windSpeed")));
                    }
                    data.setWindScale(noon.getString("windScale"));
                }
                if (data.getWeatherText() == null || data.getWeatherText().isEmpty()) {
                    boolean hasRain = data.getPrecip() != null && data.getPrecip().doubleValue() > 0.1;
                    data.setWeatherText(hasRain ? "雨" : "多云");
                }
                data.setDataType(3); // 3=历史数据
                allHistory.add(data);
            } catch (Exception e) {
                log.warn("历史天气解析失败: date={}, err={}", date, e.getMessage());
            }
        }
        return allHistory;
    }

    /**
     * 从小时天气数组中挑出最接近正午12点的一条，用于代表当天的天气状况
     */
    private JSONObject pickNoonHour(JSONArray hourly) {
        if (hourly == null || hourly.isEmpty()) return null;
        JSONObject best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < hourly.size(); i++) {
            JSONObject h = hourly.getJSONObject(i);
            String time = h.getString("time"); // 形如 2026-09-01T12:00+08:00
            if (time == null || time.length() < 13) continue;
            try {
                int hour = Integer.parseInt(time.substring(11, 13));
                int diff = Math.abs(hour - 12);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = h;
                }
            } catch (Exception ignored) {
                // 单条时间解析失败不影响整体
            }
        }
        return best;
    }

    private String doGet(String url) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            get.setHeader("User-Agent", "SmartWeather/1.0");
            try (CloseableHttpResponse response = client.execute(get)) {
                String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                log.info("API响应状态码: {}", response.getStatusLine().getStatusCode());
                log.info("API响应内容: {}", body);
                return body;
            }
        } catch (Exception e) {
            log.error("天气API请求失败: {}", e.getMessage(), e);
            return null;
        }
    }
}
