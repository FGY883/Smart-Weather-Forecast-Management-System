package com.weather.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.WeatherData;
import com.weather.entity.WeatherWarning;
import com.weather.mapper.WeatherWarningMapper;
import com.weather.service.WeatherDataService;
import com.weather.service.WeatherWarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeatherWarningServiceImpl extends ServiceImpl<WeatherWarningMapper, WeatherWarning> implements WeatherWarningService {

    @Autowired
    private WeatherDataService weatherDataService;

    @Value("${ai.warning.high-temp-threshold:35}")
    private double highTempThreshold;

    @Value("${ai.warning.low-temp-threshold:0}")
    private double lowTempThreshold;

    @Value("${ai.warning.rain-threshold:50}")
    private double rainThreshold;

    @Value("${ai.warning.wind-threshold:8}")
    private double windThreshold;

    @Override
    public List<WeatherWarning> checkAndGenerateWarnings(String cityCode, String cityName) {
        WeatherData realtime = weatherDataService.getRealtimeWeather(cityCode, cityName);
        if (realtime == null) return new ArrayList<>();

        List<WeatherWarning> warnings = new ArrayList<>();

        // 高温预警
        if (realtime.getTemp() != null && realtime.getTemp().doubleValue() >= highTempThreshold) {
            warnings.add(createWarning(cityCode, cityName, "HIGH_TEMP", "ORANGE",
                "高温橙色预警",
                cityName + "当前温度" + realtime.getTemp() + "℃，已超过" + highTempThreshold + "℃高温阈值，请注意防暑降温。"));
        } else if (realtime.getTemp() != null && realtime.getTemp().doubleValue() >= 33) {
            warnings.add(createWarning(cityCode, cityName, "HIGH_TEMP", "YELLOW",
                "高温黄色预警",
                cityName + "当前温度" + realtime.getTemp() + "℃，气温较高，请注意防暑。"));
        }

        // 寒潮预警
        if (realtime.getTemp() != null && realtime.getTemp().doubleValue() <= lowTempThreshold) {
            String level = realtime.getTemp().doubleValue() <= -10 ? "RED" : "ORANGE";
            warnings.add(createWarning(cityCode, cityName, "COLD_WAVE", level,
                "寒潮" + (level.equals("RED") ? "红色" : "橙色") + "预警",
                cityName + "当前温度" + realtime.getTemp() + "℃，已低于" + lowTempThreshold + "℃寒潮阈值，请注意保暖。"));
        }

        // 暴雨预警
        if (realtime.getPrecip() != null && realtime.getPrecip().doubleValue() >= rainThreshold) {
            warnings.add(createWarning(cityCode, cityName, "HEAVY_RAIN", "RED",
                "暴雨红色预警",
                cityName + "当前降水量" + realtime.getPrecip() + "mm，已超过暴雨阈值，请注意防范。"));
        } else if (realtime.getPrecip() != null && realtime.getPrecip().doubleValue() >= 30) {
            warnings.add(createWarning(cityCode, cityName, "HEAVY_RAIN", "ORANGE",
                "暴雨橙色预警",
                cityName + "当前降水量" + realtime.getPrecip() + "mm，降雨量较大，请注意安全。"));
        }

        // 大风预警
        if (realtime.getWindSpeed() != null && realtime.getWindSpeed().doubleValue() >= windThreshold * 10) {
            warnings.add(createWarning(cityCode, cityName, "STRONG_WIND", "YELLOW",
                "大风黄色预警",
                cityName + "当前风速" + realtime.getWindSpeed() + "km/h，风力较大，请注意安全。"));
        }

        if (!warnings.isEmpty()) {
            saveBatch(warnings);
        }
        return warnings;
    }

    @Override
    public List<WeatherWarning> getActiveWarnings(String cityCode) {
        return list(new LambdaQueryWrapper<WeatherWarning>()
            .eq(WeatherWarning::getCityCode, cityCode)
            .eq(WeatherWarning::getStatus, 1)
            .orderByDesc(WeatherWarning::getCreateTime));
    }

    @Override
    public List<WeatherWarning> getAllActiveWarnings() {
        return list(new LambdaQueryWrapper<WeatherWarning>()
            .eq(WeatherWarning::getStatus, 1)
            .orderByDesc(WeatherWarning::getCreateTime));
    }

    private WeatherWarning createWarning(String cityCode, String cityName, String type, String level, String title, String content) {
        WeatherWarning warning = new WeatherWarning();
        warning.setCity(cityName);
        warning.setCityCode(cityCode);
        warning.setWarningType(type);
        warning.setWarningLevel(level);
        warning.setWarningTitle(title);
        warning.setWarningContent(content);
        warning.setStartTime(LocalDateTime.now());
        warning.setEndTime(LocalDateTime.now().plusDays(1));
        warning.setStatus(1);
        return warning;
    }
}
