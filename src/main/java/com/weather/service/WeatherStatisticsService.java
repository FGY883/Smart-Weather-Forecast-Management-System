package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.WeatherStatistics;
import java.util.List;

public interface WeatherStatisticsService extends IService<WeatherStatistics> {
    void generateMonthlyStatistics(String cityCode, String cityName, int year, int month);
    List<WeatherStatistics> getCityStatistics(String cityCode, int year);

    /**
     * 统计该城市某年在 weather_data 中真实观测到的天数。
     * 用于判断统计结果来自实测数据还是气候常理推算，前端据此标注数据来源。
     */
    int countRealDataDays(String cityCode, int year);
}
