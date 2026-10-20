package com.weather.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.WeatherData;
import com.weather.entity.WeatherWarning;
import com.weather.mapper.WeatherWarningMapper;
import com.weather.service.WeatherDataService;
import com.weather.service.WeatherWarningService;
import com.weather.util.TimeUtil;
import com.weather.util.WeatherApiUtil;
import com.weather.util.WeatherCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeatherWarningServiceImpl extends ServiceImpl<WeatherWarningMapper, WeatherWarning> implements WeatherWarningService {

    private static final Logger log = LoggerFactory.getLogger(WeatherWarningServiceImpl.class);

    @Autowired
    private WeatherDataService weatherDataService;

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private WeatherCache cache;

    /** 预警是实时性要求高的数据，缓存窗口短一些，避免线上信息滞后 */
    private static final Duration WARNING_TTL = Duration.ofMinutes(5);

    @Value("${ai.warning.high-temp-threshold:35}")
    private double highTempThreshold;

    @Value("${ai.warning.low-temp-threshold:0}")
    private double lowTempThreshold;

    @Value("${ai.warning.rain-threshold:50}")
    private double rainThreshold;

    @Value("${ai.warning.wind-threshold:8}")
    private double windThreshold;

    /** 高温黄色预警触发线（低于橙色阈值时的降级档） */
    @Value("${ai.warning.high-temp-yellow:33}")
    private double highTempYellow;

    /** 暴雨橙色预警触发线 */
    @Value("${ai.warning.rain-orange:30}")
    private double rainOrange;

    /** 寒潮红色预警触发线 */
    @Value("${ai.warning.cold-red:-10}")
    private double coldRed;

    @Override
    public List<WeatherWarning> checkAndGenerateWarnings(String cityCode, String cityName) {
        // 先把过期的预警解除掉，再判断当前是否仍需要新预警
        expireOutdatedWarnings();

        WeatherData realtime = weatherDataService.getRealtimeWeather(cityCode, cityName);
        if (realtime == null) return new ArrayList<>();

        List<WeatherWarning> warnings = new ArrayList<>();

        // 高温预警
        if (realtime.getTemp() != null && realtime.getTemp().doubleValue() >= highTempThreshold) {
            warnings.add(createWarning(cityCode, cityName, "HIGH_TEMP", "ORANGE",
                "高温橙色预警",
                cityName + "当前温度" + realtime.getTemp() + "℃，已超过" + highTempThreshold + "℃高温阈值，请注意防暑降温。"));
        } else if (realtime.getTemp() != null && realtime.getTemp().doubleValue() >= highTempYellow) {
            warnings.add(createWarning(cityCode, cityName, "HIGH_TEMP", "YELLOW",
                "高温黄色预警",
                cityName + "当前温度" + realtime.getTemp() + "℃，气温较高，请注意防暑。"));
        }

        // 寒潮预警
        if (realtime.getTemp() != null && realtime.getTemp().doubleValue() <= lowTempThreshold) {
            String level = realtime.getTemp().doubleValue() <= coldRed ? "RED" : "ORANGE";
            warnings.add(createWarning(cityCode, cityName, "COLD_WAVE", level,
                "寒潮" + (level.equals("RED") ? "红色" : "橙色") + "预警",
                cityName + "当前温度" + realtime.getTemp() + "℃，已低于" + lowTempThreshold + "℃寒潮阈值，请注意保暖。"));
        }

        // 暴雨预警
        if (realtime.getPrecip() != null && realtime.getPrecip().doubleValue() >= rainThreshold) {
            warnings.add(createWarning(cityCode, cityName, "HEAVY_RAIN", "RED",
                "暴雨红色预警",
                cityName + "当前降水量" + realtime.getPrecip() + "mm，已超过暴雨阈值，请注意防范。"));
        } else if (realtime.getPrecip() != null && realtime.getPrecip().doubleValue() >= rainOrange) {
            warnings.add(createWarning(cityCode, cityName, "HEAVY_RAIN", "ORANGE",
                "暴雨橙色预警",
                cityName + "当前降水量" + realtime.getPrecip() + "mm，降雨量较大，请注意安全。"));
        }

        // 大风预警（和风风速单位为 km/h，这里按 1 级风 ≈ 10km/h 折算阈值）
        if (realtime.getWindSpeed() != null && realtime.getWindSpeed().doubleValue() >= windThreshold * 10) {
            warnings.add(createWarning(cityCode, cityName, "STRONG_WIND", "YELLOW",
                "大风黄色预警",
                cityName + "当前风速" + realtime.getWindSpeed() + "km/h，风力较大，请注意安全。"));
        }

        if (!warnings.isEmpty()) {
            // 同一城市同一类型只保留一条生效预警，避免定时任务反复插入
            for (WeatherWarning w : warnings) {
                if (!hasActiveWarning(cityCode, w.getWarningType())) {
                    save(w);
                }
            }
        }
        return getActiveWarnings(cityCode);
    }

    @Override
    public List<WeatherWarning> getActiveWarnings(String cityCode) {
        return list(activeWrapper().eq(WeatherWarning::getCityCode, cityCode));
    }

    @Override
    public List<WeatherWarning> getAllActiveWarnings() {
        return list(activeWrapper());
    }

    @Override
    public int expireOutdatedWarnings() {
        // 计数已过期的活跃预警条数，再统一置为无效（IService.update 返回 boolean）
        LambdaQueryWrapper<WeatherWarning> wrapper = new LambdaQueryWrapper<WeatherWarning>()
                .eq(WeatherWarning::getStatus, 1)
                .lt(WeatherWarning::getEndTime, TimeUtil.now());
        int rows = Math.toIntExact(count(wrapper));
        if (rows > 0) {
            WeatherWarning update = new WeatherWarning();
            update.setStatus(0);
            update(update, wrapper);
            log.info("自动解除过期预警: {} 条", rows);
        }
        return rows;
    }

    /**
     * 生效中的判定条件：状态为 1 且尚未到结束时间
     */
    private LambdaQueryWrapper<WeatherWarning> activeWrapper() {
        return new LambdaQueryWrapper<WeatherWarning>()
                .eq(WeatherWarning::getStatus, 1)
                .and(w -> w.isNull(WeatherWarning::getEndTime)
                          .or().gt(WeatherWarning::getEndTime, TimeUtil.now()))
                .orderByDesc(WeatherWarning::getCreateTime);
    }

    private boolean hasActiveWarning(String cityCode, String type) {
        return count(new LambdaQueryWrapper<WeatherWarning>()
                .eq(WeatherWarning::getCityCode, cityCode)
                .eq(WeatherWarning::getWarningType, type)
                .eq(WeatherWarning::getStatus, 1)
                .and(w -> w.isNull(WeatherWarning::getEndTime)
                          .or().gt(WeatherWarning::getEndTime, TimeUtil.now()))) > 0;
    }

    private WeatherWarning createWarning(String cityCode, String cityName, String type, String level, String title, String content) {
        WeatherWarning warning = new WeatherWarning();
        warning.setCity(cityName);
        warning.setCityCode(cityCode);
        warning.setWarningType(type);
        warning.setWarningLevel(level);
        warning.setWarningTitle(title);
        warning.setWarningContent(content);
        warning.setStartTime(TimeUtil.now());
        warning.setEndTime(TimeUtil.now().plusDays(1));
        warning.setStatus(1);
        return warning;
    }

    @Override
    public JSONArray fetchWarningsFromApi(String cityCode) {
        if (cityCode == null || cityCode.isEmpty()) return new JSONArray();
        String key = "warn:api:" + cityCode;
        JSONArray cached = cache.get(key, JSONArray.class);
        if (cached != null) {
            return cached;
        }
        JSONArray fresh = weatherApiUtil.fetchActiveWarnings(cityCode);
        // 接口失败(null)时返回空但不写缓存，避免把"暂无预警"缓存住而隐藏真实预警 WARNING_TTL 时长
        if (fresh == null) {
            return new JSONArray();
        }
        cache.put(key, fresh, WARNING_TTL);
        return fresh;
    }

    @Override
    public JSONArray fetchAllWarningsFromApi() {
        String key = "warn:api:all";
        JSONArray cached = cache.get(key, JSONArray.class);
        if (cached != null) {
            return cached;
        }
        JSONArray fresh = weatherApiUtil.fetchAllActiveWarnings();
        if (fresh == null) {
            return new JSONArray();
        }
        cache.put(key, fresh, WARNING_TTL);
        return fresh;
    }

    @Override
    public JSONArray fetchWarningsFromWeathercn(String cityName) {
        if (cityName == null || cityName.isEmpty()) {
            return new JSONArray();
        }
        String key = "warn:wc:" + cityName;
        JSONArray cached = cache.get(key, JSONArray.class);
        if (cached != null) {
            return cached;
        }
        // 城市名 → Location Key → 预警列表
        String locationKey = weatherApiUtil.searchCityWeathercn(cityName);
        if (locationKey == null) {
            return new JSONArray();
        }
        JSONArray raw = weatherApiUtil.fetchAlertsWeathercn(locationKey);
        if (raw == null) {
            return new JSONArray();   // 失败不缓存，避免隐藏真实预警
        }
        JSONArray result = normalizeWeathercnAlerts(raw);
        cache.put(key, result, WARNING_TTL);
        return result;
    }

    /**
     * 把华风爱科预警的原始字段拍平成模板统一消费的结构。
     * 华风用 AlarmLevel(Red/Orange/Yellow/Blue) + Level(红色/橙色)，
     * 这里同时保留英文（供 CSS class level-* 用）和中文（供页面文字显示用）。
     */
    private JSONArray normalizeWeathercnAlerts(JSONArray raw) {
        JSONArray result = new JSONArray();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (int i = 0; i < raw.size(); i++) {
            JSONObject a = raw.getJSONObject(i);
            if (a == null) continue;
            JSONObject w = new JSONObject();

            JSONObject desc = a.getJSONObject("Description");
            w.put("title", desc != null && desc.getString("Localized") != null
                    ? desc.getString("Localized") : a.getString("Type"));
            w.put("typeName", a.getString("Type"));
            w.put("type", a.getString("Category"));
            // 英文等级用于 CSS 配色（level-Red / level-Orange / level-Yellow / level-Blue）
            w.put("level", a.getString("AlarmLevel"));
            // 中文等级用于页面文字（红色预警 / 橙色预警 ...）
            w.put("levelName", a.getString("Level"));
            w.put("sender", a.getString("Source"));

            JSONArray areas = a.getJSONArray("Area");
            if (areas != null && !areas.isEmpty()) {
                JSONObject area = areas.getJSONObject(0);
                if (area != null) {
                    w.put("areaName", area.getString("Name"));
                    w.put("startTime", area.getString("StartTime"));
                    w.put("endTime", area.getString("EndTime"));
                    w.put("text", area.getString("Text"));
                    w.put("summary", area.getString("Summary"));
                }
            }

            JSONObject color = a.getJSONObject("Color");
            if (color != null) {
                w.put("hexColor", color.getString("Hex"));
            }
            w.put("status", "Active");
            result.add(w);
        }
        return result;
    }
}
