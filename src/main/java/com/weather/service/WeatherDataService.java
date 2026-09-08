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
}
