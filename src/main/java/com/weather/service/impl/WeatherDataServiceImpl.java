package com.weather.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.WeatherData;
import com.weather.mapper.WeatherDataMapper;
import com.weather.service.WeatherDataService;
import com.weather.util.WeatherApiUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class WeatherDataServiceImpl extends ServiceImpl<WeatherDataMapper, WeatherData> implements WeatherDataService {

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Override
    public WeatherData getRealtimeWeather(String cityCode, String cityName) {
        WeatherData data = weatherApiUtil.getRealtimeWeather(cityCode, cityName);
        if (data != null) {
            save(data);
        }
        return data;
    }

    @Override
    public List<WeatherData> getForecast7Day(String cityCode, String cityName) {
        List<WeatherData> forecast = weatherApiUtil.getForecast7Day(cityCode, cityName);
        if (forecast != null && !forecast.isEmpty()) {
            saveBatch(forecast);
        }
        return forecast;
    }

    @Override
    public List<WeatherData> getHistoryData(String cityCode, LocalDate start, LocalDate end) {
        return baseMapper.selectByCityAndDateRange(cityCode, start, end);
    }

    @Override
    public List<WeatherData> getRecentHistory(String cityCode, int limit) {
        return baseMapper.selectRecentHistory(cityCode, limit);
    }
}
