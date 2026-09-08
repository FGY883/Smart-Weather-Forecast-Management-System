package com.weather.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weather.entity.WeatherData;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
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

    /** 华风爱科 API Key（灾害预警数据源，和风预警为付费接口） */
    @Value("${weather.weathercn.api-key:}")
    private String weathercnApiKey;

    /** 华风爱科域名 */
    @Value("${weather.weathercn.base-url:https://openapi.weathercn.com}")
    private String weathercnBaseUrl;

    /** 连接/读取超时（毫秒）：外部接口抖动时不能把 Tomcat 线程一直占住 */
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 6000;
    private static final int MAX_RETRY = 2;

    /**
     * 全局共享的连接池。
     * 原先每次请求都 HttpClients.createDefault()，既不复用连接也没有超时设置，
     * 接口变慢时会迅速耗尽线程。
     */
    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setConnectionManager(new PoolingHttpClientConnectionManager())
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                    .setSocketTimeout(READ_TIMEOUT)
                    .build())
            .setRetryHandler(new DefaultHttpRequestRetryHandler(MAX_RETRY, true))
            .build();

    /**
     * 查询城市信息
     */
    public JSONObject searchCity(String location) {
        try {
            String encodedLocation = URLEncoder.encode(location, "UTF-8");
            String url = geoUrl + "/v2/city/lookup?location=" + encodedLocation + "&key=" + apiKey;
            log.debug("搜索城市URL: {}", url);
            String resp = doGet(url);
            if (resp != null) {
                JSONObject json = JSONObject.parseObject(resp);
                if ("200".equals(json.getString("code"))) {
                    return json;
                } else {
                    log.warn("城市搜索API返回错误: code={}", json.getString("code"));
                }
            }
        } catch (Exception e) {
            log.error("城市搜索URL编码失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 直接调用和风天气灾害预警接口获取某城市正在生效的预警列表。
     *
     * 返回原始 JSON 数组，不做业务映射。调用方拿到 JSON 后可按需渲染到模板或落库。
     * 接口返回码 200 时 warning 数组可能为空但仍属成功；返回 404 / 其他码视为调用失败返回 null。
     */
    public JSONArray fetchActiveWarnings(String cityCode) {
        if (cityCode == null || cityCode.isEmpty()) {
            return null;
        }
        String url = baseUrl + "/warning/now?location=" + cityCode + "&key=" + apiKey;
        log.debug("查询灾害预警URL: {}", url);
        String resp = doGet(url);
        if (resp == null) return null;
        try {
            JSONObject json = JSONObject.parseObject(resp);
            String code = json.getString("code");
            // 200 表示正常返回（含 warning 为空），其他码视为失败
            if (!"200".equals(code)) {
                log.warn("灾害预警API返回错误: cityCode={}, code={}, message={}", cityCode, code, json.getString("message"));
                return null;
            }
            JSONArray warning = json.getJSONArray("warning");
            return warning != null ? warning : new JSONArray();
        } catch (Exception e) {
            log.warn("灾害预警响应解析失败: cityCode={}, err={}", cityCode, e.getMessage());
            return null;
        }
    }

    /**
     * 获取全国当前所有正在生效的预警，按城市聚合（用于预警列表页）。
     * 接口：/v7/warning/list?key=xxx
     */
    public JSONArray fetchAllActiveWarnings() {
        String url = baseUrl + "/warning/list?key=" + apiKey;
        log.debug("查询全国活跃预警URL: {}", url);
        String resp = doGet(url);
        if (resp == null) return null;
        try {
            JSONObject json = JSONObject.parseObject(resp);
            String code = json.getString("code");
            if (!"200".equals(code)) {
                log.warn("全国预警API返回错误: code={}, message={}", code, json.getString("message"));
                return null;
            }
            JSONArray list = json.getJSONArray("warningLocList");
            return list != null ? list : new JSONArray();
        } catch (Exception e) {
            log.warn("全国预警响应解析失败: err={}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取实时天气
     */
    public WeatherData getRealtimeWeather(String cityCode, String cityName) {
        String url = baseUrl + "/weather/now?location=" + cityCode + "&key=" + apiKey;
        log.debug("获取实时天气URL: {}", url);
        String resp = doGet(url);
        log.debug("实时天气响应: {}", resp);
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
        log.debug("获取7天预报URL: {}", url);
        String resp = doGet(url);
        log.debug("7天预报响应: {}", resp);
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
            log.debug("获取历史天气URL: {}", url);
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
        HttpGet get = new HttpGet(url);
        get.setHeader("User-Agent", "SmartWeather/1.0");
        try (CloseableHttpResponse response = HTTP_CLIENT.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            if (status != 200) {
                log.warn("天气API返回异常状态码: {}, url={}", status, url);
                return null;
            }
            return EntityUtils.toString(response.getEntity(), "UTF-8");
        } catch (Exception e) {
            log.error("天气API请求失败: {}", e.getMessage());
            return null;
        }
    }

    /* ==================== 华风爱科（灾害预警数据源） ==================== */

    /**
     * 华风爱科城市搜索：把城市名转成 Location Key。
     * 接口 /locations/v1/cities/translate 返回 JSON 数组，取第一条的 Key。
     *
     * @return Location Key（如北京 101924），失败返回 null
     */
    public String searchCityWeathercn(String cityName) {
        if (cityName == null || cityName.isEmpty() || weathercnApiKey.isEmpty()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(cityName, "UTF-8");
            String url = weathercnBaseUrl + "/locations/v1/cities/translate"
                    + "?apikey=" + weathercnApiKey
                    + "&q=" + encoded
                    + "&language=zh-cn";
            log.debug("华风城市搜索URL: {}", url);
            String resp = doGet(url);
            if (resp == null) return null;
            JSONArray arr = JSONArray.parseArray(resp);
            if (arr == null || arr.isEmpty()) {
                log.warn("华风城市搜索无结果: city={}", cityName);
                return null;
            }
            JSONObject first = arr.getJSONObject(0);
            return first.getString("Key");
        } catch (Exception e) {
            log.warn("华风城市搜索失败: city={}, err={}", cityName, e.getMessage());
            return null;
        }
    }

    /**
     * 华风爱科预警查询：按 Location Key 拉当前生效预警列表。
     * 接口 /alerts/v1/{locationkey}.json 返回 JSON 数组（无预警时为空 []）。
     *
     * @return 预警对象数组，失败返回 null
     */
    public JSONArray fetchAlertsWeathercn(String locationKey) {
        if (locationKey == null || locationKey.isEmpty() || weathercnApiKey.isEmpty()) {
            return null;
        }
        try {
            String url = weathercnBaseUrl + "/alerts/v1/" + locationKey + ".json"
                    + "?apikey=" + weathercnApiKey
                    + "&language=zh-cn"
                    + "&details=true";
            log.debug("华风预警查询URL: {}", url);
            String resp = doGet(url);
            if (resp == null) return null;
            JSONArray arr = JSONArray.parseArray(resp);
            return arr != null ? arr : new JSONArray();
        } catch (Exception e) {
            log.warn("华风预警查询失败: locationKey={}, err={}", locationKey, e.getMessage());
            return null;
        }
    }
}
