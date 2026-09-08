package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.WeatherWarning;
import java.util.List;

public interface WeatherWarningService extends IService<WeatherWarning> {
    List<WeatherWarning> checkAndGenerateWarnings(String cityCode, String cityName);
    List<WeatherWarning> getActiveWarnings(String cityCode);
    List<WeatherWarning> getAllActiveWarnings();
}
