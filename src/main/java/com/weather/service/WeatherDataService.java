package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.WeatherData;
import java.time.LocalDate;
import java.util.List;

public interface WeatherDataService extends IService<WeatherData> {
    WeatherData getRealtimeWeather(String cityCode, String cityName);
    List<WeatherData> getForecast7Day(String cityCode, String cityName);
    List<WeatherData> getHistoryData(String cityCode, LocalDate start, LocalDate end);
    List<WeatherData> getRecentHistory(String cityCode, int limit);

    /**
     * 按 (城市, 日期, 数据类型) 去重写入：已存在则更新，否则新增。
     * 用于替代 saveBatch，避免每一次页面访问都在 weather_data 里堆一行重复数据。
     */
    void saveOrUpdateData(List<WeatherData> list);
}
