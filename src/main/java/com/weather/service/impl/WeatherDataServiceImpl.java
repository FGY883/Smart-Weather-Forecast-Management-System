package com.weather.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.WeatherData;
import com.weather.mapper.WeatherDataMapper;
import com.weather.service.WeatherDataService;
import com.weather.util.WeatherApiUtil;
import com.weather.util.WeatherCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
public class WeatherDataServiceImpl extends ServiceImpl<WeatherDataMapper, WeatherData> implements WeatherDataService {

    private static final Logger log = LoggerFactory.getLogger(WeatherDataServiceImpl.class);

    /** 实况变化快，缓存 10 分钟；预报一天内基本不变，缓存 30 分钟 */
    private static final Duration REALTIME_TTL = Duration.ofMinutes(10);
    private static final Duration FORECAST_TTL = Duration.ofMinutes(30);
    private static final Duration HISTORY_TTL = Duration.ofHours(6);

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private WeatherCache cache;

    @Override
    public WeatherData getRealtimeWeather(String cityCode, String cityName) {
        WeatherData data = cache.getOrLoad("rt:" + cityCode, WeatherData.class, REALTIME_TTL,
                () -> weatherApiUtil.getRealtimeWeather(cityCode, cityName));
        if (data == null) {
            return null;
        }
        // 实况接口不返回当日最高/最低温与紫外线，从当日预报行补齐，
        // 否则详情页这几项永远显示 "--"
        enrichWithDailyRange(data, cityCode, cityName);
        saveOrUpdateOne(data);
        return data;
    }

    @Override
    public List<WeatherData> getForecast7Day(String cityCode, String cityName) {
        List<WeatherData> forecast = cache.getOrLoad("fc:" + cityCode, List.class, FORECAST_TTL,
                () -> weatherApiUtil.getForecast7Day(cityCode, cityName));
        if (forecast != null && !forecast.isEmpty()) {
            // 缓存返回的是同一份对象，写库前复制一份，避免 id 被回写污染缓存
            saveOrUpdateData(forecast);
        }
        return forecast;
    }

    @Override
    public List<WeatherData> getHistoryData(String cityCode, LocalDate start, LocalDate end) {
        String key = "hist:" + cityCode + ":" + start + ":" + end;
        List<WeatherData> cached = cache.get(key, List.class);
        if (cached != null) {
            return cached;
        }
        List<WeatherData> data = baseMapper.selectByCityAndDateRange(cityCode, start, end);
        if (data != null && !data.isEmpty()) {
            cache.put(key, data, HISTORY_TTL);
        }
        return data;
    }

    @Override
    public List<WeatherData> getRecentHistory(String cityCode, int limit) {
        return baseMapper.selectRecentHistory(cityCode, limit);
    }

    @Override
    public void saveOrUpdateData(List<WeatherData> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (WeatherData d : list) {
            saveOrUpdateOne(d);
        }
    }

    /**
     * 用当日预报补齐实况缺失的日最高/最低温、紫外线指数
     */
    private void enrichWithDailyRange(WeatherData realtime, String cityCode, String cityName) {
        if (realtime == null || realtime.getWeatherDate() == null) {
            return;
        }
        try {
            List<WeatherData> forecast = getForecast7Day(cityCode, cityName);
            if (forecast == null || forecast.isEmpty()) {
                return;
            }
            LocalDate today = realtime.getWeatherDate();
            for (WeatherData day : forecast) {
                if (today.equals(day.getWeatherDate())) {
                    if (realtime.getTempMax() == null) realtime.setTempMax(day.getTempMax());
                    if (realtime.getTempMin() == null) realtime.setTempMin(day.getTempMin());
                    if (realtime.getUvIndex() == null) realtime.setUvIndex(day.getUvIndex());
                    return;
                }
            }
        } catch (Exception e) {
            // 补齐失败不影响实况本身的展示
        }
    }

    /**
     * 同城市同日期同类型只保留一行：已存在就更新，避免重复堆积。
     * 关键修复：
     *  - 对入参做副本再落库，绝不把数据库 id 回写进调用方（可能是缓存里的）对象，避免跨请求污染；
     *  - 并发下两个线程同时"查不到→插入"会撞唯一索引，捕获 DuplicateKeyException 后转为更新，避免静默丢数据；
     *  - 写库成功后清掉该城市的 hist: 读缓存，保证统计/回测读到最新数据。
     */
    private void saveOrUpdateOne(WeatherData data) {
        if (data == null || data.getCityCode() == null || data.getWeatherDate() == null) {
            return;
        }
        WeatherData row = new WeatherData();
        BeanUtils.copyProperties(data, row);
        row.setId(null);
        try {
            WeatherData exist = findExisting(row);
            if (exist != null) {
                row.setId(exist.getId());
                updateById(row);
            } else {
                save(row);
            }
            cache.evictPrefix("hist:" + row.getCityCode() + ":");
        } catch (DuplicateKeyException dup) {
            // 并发插入冲突：改为更新既有行
            try {
                WeatherData exist = findExisting(row);
                if (exist != null) {
                    row.setId(exist.getId());
                    updateById(row);
                }
                cache.evictPrefix("hist:" + row.getCityCode() + ":");
            } catch (Exception ignore) {
                log.warn("天气数据并发写库失败(转更新仍失败): city={}, date={}, type={}",
                        row.getCityCode(), row.getWeatherDate(), row.getDataType());
            }
        } catch (Exception e) {
            // 写库失败不应影响页面展示，但要留痕，避免数据完整性被悄悄破坏
            log.warn("天气数据写库失败: city={}, date={}, type={}, err={}",
                    row.getCityCode(), row.getWeatherDate(), row.getDataType(), e.getMessage());
        }
    }

    private WeatherData findExisting(WeatherData row) {
        return getOne(new LambdaQueryWrapper<WeatherData>()
                .eq(WeatherData::getCityCode, row.getCityCode())
                .eq(WeatherData::getWeatherDate, row.getWeatherDate())
                .eq(WeatherData::getDataType, row.getDataType())
                .last("LIMIT 1"));
    }
}
