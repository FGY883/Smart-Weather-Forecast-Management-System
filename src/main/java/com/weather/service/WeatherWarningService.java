package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.WeatherWarning;
import java.util.List;

public interface WeatherWarningService extends IService<WeatherWarning> {
    List<WeatherWarning> checkAndGenerateWarnings(String cityCode, String cityName);
    List<WeatherWarning> getActiveWarnings(String cityCode);
    List<WeatherWarning> getAllActiveWarnings();

    /**
     * 解除已过期的预警（end_time 早于当前时间）。
     * 预警生成后不会自动消失，必须定期调用，否则列表会无限堆积。
     */
    int expireOutdatedWarnings();
}
