package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.WeatherStatistics;
import java.util.List;

public interface WeatherStatisticsService extends IService<WeatherStatistics> {
    void generateMonthlyStatistics(String cityCode, String cityName, int year, int month);
    List<WeatherStatistics> getCityStatistics(String cityCode, int year);
}
