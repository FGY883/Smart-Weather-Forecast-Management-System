package com.weather.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.WeatherData;
import com.weather.entity.WeatherStatistics;
import com.weather.mapper.WeatherStatisticsMapper;
import com.weather.service.WeatherDataService;
import com.weather.service.WeatherStatisticsService;
import com.weather.util.WeatherApiUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@Service
public class WeatherStatisticsServiceImpl extends ServiceImpl<WeatherStatisticsMapper, WeatherStatistics> implements WeatherStatisticsService {

    private static final Logger log = LoggerFactory.getLogger(WeatherStatisticsServiceImpl.class);

    @Autowired
    private WeatherDataService weatherDataService;

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private WeatherCache cache;

    private static final Duration STATS_TTL = Duration.ofHours(6);

    // 气候常理数据：{avgTemp, maxTemp, minTemp, humidity, precip, rainyDays, sunnyDays, cloudyDays}
    private static final Map<String, double[][]> CLIMATE_NORMALS = new HashMap<>();

    static {
        // 北京气候数据
        CLIMATE_NORMALS.put("北京", new double[][] {
            {-3.7, 2.3, -8.7, 44, 2.7, 1.5, 18, 11.5},
            {-0.7, 5.9, -5.8, 44, 4.9, 2.5, 16, 9.5},
            {6.5, 13.3, 0.4, 42, 8.3, 3.5, 18, 9.5},
            {14.8, 21.3, 8.5, 44, 21.2, 5.5, 17, 7.5},
            {20.8, 27.2, 14.7, 50, 34.2, 6.5, 18, 6.5},
            {25.3, 31.1, 19.9, 60, 78.1, 8.5, 17, 4.5},
            {27.3, 32.6, 22.5, 72, 185.2, 11.5, 15, 4.5},
            {25.8, 31.0, 21.2, 73, 159.0, 10.5, 16, 4.5},
            {20.8, 27.0, 15.2, 63, 50.1, 6.5, 18, 5.5},
            {13.2, 19.8, 7.4, 58, 16.8, 4.5, 18, 8.5},
            {4.8, 11.0, -0.5, 52, 5.3, 2.5, 17, 10.5},
            {-1.8, 4.3, -6.8, 47, 2.0, 1.5, 18, 11.5}
        });

        // 上海气候数据
        CLIMATE_NORMALS.put("上海", new double[][] {
            {4.2, 8.1, 1.2, 72, 48.0, 8.5, 10, 12.5},
            {5.5, 9.5, 2.3, 71, 60.0, 9.5, 9, 9.5},
            {9.5, 13.8, 6.0, 72, 84.0, 11.5, 10, 9.5},
            {15.5, 20.5, 11.2, 72, 94.0, 12.5, 11, 6.5},
            {20.8, 25.5, 16.8, 73, 115.0, 11.5, 12, 7.5},
            {24.8, 29.2, 21.2, 79, 152.0, 11.5, 11, 7.5},
            {28.8, 33.5, 25.2, 77, 142.0, 10.5, 14, 6.5},
            {28.5, 33.0, 25.0, 76, 130.0, 9.5, 15, 6.5},
            {24.5, 29.0, 21.0, 75, 130.0, 9.5, 14, 6.5},
            {19.0, 23.8, 15.2, 71, 60.0, 7.5, 14, 9.5},
            {12.8, 17.5, 9.0, 70, 50.0, 7.5, 13, 9.5},
            {6.5, 11.0, 3.0, 71, 38.0, 6.5, 12, 12.5}
        });

        // 广州气候数据
        CLIMATE_NORMALS.put("广州", new double[][] {
            {14.2, 18.8, 10.8, 68, 40.0, 5.5, 13, 12.5},
            {15.5, 19.8, 12.0, 73, 65.0, 8.5, 9, 10.5},
            {18.5, 22.8, 15.2, 78, 85.0, 10.5, 8, 12.5},
            {22.8, 27.0, 19.5, 82, 175.0, 12.5, 7, 10.5},
            {26.2, 30.5, 23.0, 83, 200.0, 14.5, 9, 7.5},
            {28.2, 32.5, 25.2, 84, 280.0, 15.5, 10, 4.5},
            {29.5, 33.8, 26.5, 82, 230.0, 13.5, 14, 3.5},
            {29.2, 33.5, 26.2, 82, 220.0, 12.5, 14, 4.5},
            {27.8, 32.0, 24.8, 78, 170.0, 10.5, 15, 4.5},
            {24.2, 28.8, 21.0, 72, 75.0, 6.5, 17, 7.5},
            {19.5, 24.2, 16.0, 68, 40.0, 4.5, 17, 8.5},
            {15.2, 20.0, 11.5, 66, 25.0, 3.5, 17, 10.5}
        });

        // 深圳气候数据
        CLIMATE_NORMALS.put("深圳", new double[][] {
            {15.5, 19.8, 12.2, 68, 30.0, 5.0, 14, 12.0},
            {16.2, 20.5, 12.8, 72, 50.0, 7.5, 10, 10.5},
            {19.2, 23.5, 15.8, 77, 70.0, 9.5, 9, 12.5},
            {23.5, 27.8, 20.2, 82, 150.0, 11.5, 8, 10.5},
            {26.8, 31.0, 23.8, 84, 200.0, 14.0, 9, 8.0},
            {28.5, 32.5, 25.8, 85, 290.0, 16.0, 10, 4.0},
            {29.8, 33.8, 27.0, 83, 260.0, 14.0, 14, 3.0},
            {29.5, 33.5, 26.8, 83, 250.0, 13.0, 14, 4.0},
            {28.0, 32.0, 25.2, 79, 180.0, 10.5, 16, 3.5},
            {24.8, 29.5, 21.5, 72, 70.0, 6.0, 18, 7.0},
            {20.2, 25.0, 16.8, 68, 35.0, 4.0, 18, 8.0},
            {16.0, 20.8, 12.5, 66, 20.0, 3.0, 18, 10.0}
        });

        // 成都气候数据
        CLIMATE_NORMALS.put("成都", new double[][] {
            {5.8, 9.5, 2.8, 78, 8.0, 4.5, 6, 19.5},
            {7.8, 11.5, 4.8, 76, 12.0, 5.5, 6, 16.5},
            {12.2, 16.8, 8.5, 74, 20.0, 7.5, 7, 15.5},
            {17.5, 22.5, 13.5, 72, 45.0, 10.5, 8, 11.5},
            {21.8, 26.8, 17.8, 72, 80.0, 11.5, 8, 10.5},
            {24.5, 29.2, 20.8, 78, 110.0, 12.5, 6, 10.5},
            {25.8, 30.5, 22.2, 82, 220.0, 14.5, 6, 9.5},
            {25.5, 30.0, 22.0, 82, 200.0, 13.5, 7, 10.5},
            {21.5, 26.0, 18.2, 82, 130.0, 12.5, 4, 13.5},
            {17.0, 21.5, 13.5, 80, 45.0, 9.5, 4, 17.5},
            {12.0, 16.2, 8.8, 80, 15.0, 5.5, 4, 19.5},
            {7.2, 11.0, 4.2, 80, 5.0, 3.5, 5, 20.5}
        });

        // 杭州气候数据
        CLIMATE_NORMALS.put("杭州", new double[][] {
            {4.5, 8.5, 1.5, 72, 60.0, 9.5, 10, 11.5},
            {6.2, 10.2, 3.0, 73, 75.0, 10.5, 8, 9.5},
            {10.5, 15.0, 6.8, 74, 110.0, 12.5, 9, 9.5},
            {16.8, 21.8, 12.5, 74, 110.0, 12.5, 10, 7.5},
            {21.8, 26.8, 17.5, 74, 130.0, 12.5, 11, 7.5},
            {25.2, 30.0, 21.2, 80, 200.0, 13.5, 9, 7.5},
            {29.2, 34.5, 25.2, 77, 160.0, 11.5, 14, 5.5},
            {28.8, 34.0, 25.0, 77, 140.0, 10.5, 15, 5.5},
            {24.2, 29.0, 20.5, 78, 130.0, 10.5, 12, 7.5},
            {18.8, 23.8, 14.8, 74, 65.0, 7.5, 13, 10.5},
            {12.5, 17.2, 8.5, 73, 50.0, 7.5, 12, 10.5},
            {6.2, 10.8, 2.5, 72, 40.0, 6.5, 11, 13.5}
        });

        // 武汉气候数据
        CLIMATE_NORMALS.put("武汉", new double[][] {
            {3.5, 8.0, 0.2, 72, 40.0, 7.5, 11, 12.5},
            {5.5, 10.0, 2.0, 71, 55.0, 8.5, 9, 10.5},
            {10.5, 15.5, 6.5, 72, 75.0, 10.5, 10, 10.5},
            {17.0, 22.5, 12.5, 72, 100.0, 11.5, 11, 7.5},
            {22.2, 27.5, 18.0, 72, 130.0, 11.5, 12, 7.5},
            {26.2, 31.5, 22.0, 77, 180.0, 11.5, 11, 7.5},
            {29.5, 34.8, 25.5, 76, 160.0, 10.5, 16, 4.5},
            {29.0, 34.2, 25.0, 75, 120.0, 9.5, 17, 4.5},
            {24.2, 29.5, 20.0, 73, 70.0, 7.5, 15, 7.5},
            {18.0, 23.5, 13.5, 72, 50.0, 7.5, 14, 9.5},
            {11.2, 16.5, 7.0, 72, 40.0, 6.5, 13, 10.5},
            {5.0, 10.0, 1.2, 71, 25.0, 5.5, 12, 13.5}
        });

        // 默认气候数据
        CLIMATE_NORMALS.put("default", new double[][] {
            {2.0, 7.0, -2.0, 55, 10.0, 3.0, 15, 13.0},
            {4.5, 9.5, 0.5, 53, 12.0, 3.5, 14, 10.5},
            {9.5, 15.0, 5.0, 50, 18.0, 4.5, 16, 10.5},
            {16.0, 22.0, 10.5, 52, 30.0, 6.0, 16, 8.0},
            {21.5, 27.5, 16.0, 58, 50.0, 7.0, 16, 8.0},
            {25.5, 31.0, 20.5, 65, 80.0, 8.0, 15, 7.0},
            {28.0, 33.5, 23.5, 72, 150.0, 10.0, 14, 7.0},
            {27.0, 32.5, 22.5, 72, 130.0, 9.5, 15, 6.5},
            {22.5, 28.0, 18.0, 65, 55.0, 6.5, 16, 7.5},
            {16.0, 22.0, 11.0, 60, 25.0, 4.5, 16, 10.5},
            {9.0, 14.5, 4.5, 58, 12.0, 3.5, 15, 11.5},
            {3.5, 8.5, -0.5, 55, 8.0, 2.5, 15, 13.5}
        });
    }

    @Override
    public void generateMonthlyStatistics(String cityCode, String cityName, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        List<WeatherData> dataList = weatherDataService.getHistoryData(cityCode, start, end);

        WeatherStatistics stats;

        // 如果有足够的真实数据，使用真实数据计算
        if (dataList != null && dataList.size() >= 5) {
            stats = calculateFromRealData(cityCode, cityName, year, month, dataList);
            log.info("使用真实数据生成统计: {}-{}-{}, 数据条数: {}", cityName, year, month, dataList.size());
        } else {
            // 尝试从API获取7天预报数据作为参考
            List<WeatherData> forecastData = getForecastDataForReference(cityCode, cityName);
            if (forecastData != null && !forecastData.isEmpty() && isCurrentMonth(year, month)) {
                // 当前月份使用预报数据
                stats = calculateFromForecastData(cityCode, cityName, year, month, forecastData);
                log.info("使用7天预报数据生成统计: {}-{}-{}", cityName, year, month);
            } else {
                // 其他月份使用气候常理数据
                stats = generateFromClimateNormal(cityCode, cityName, year, month);
                log.info("使用气候常理数据生成统计: {}-{}-{}", cityName, year, month);
            }
        }

        saveOrUpdate(stats, new LambdaQueryWrapper<WeatherStatistics>()
            .eq(WeatherStatistics::getCityCode, cityCode)
            .eq(WeatherStatistics::getStatYear, year)
            .eq(WeatherStatistics::getStatMonth, month));
        // 该城市该年的统计已变化，清掉列表缓存
        cache.evict(statsCacheKey(cityCode, year));
    }

    private String statsCacheKey(String cityCode, int year) {
        return "stats:" + cityCode + ":" + year;
    }

    /**
     * 判断是否是当前月份
     */
    private boolean isCurrentMonth(int year, int month) {
        LocalDate now = LocalDate.now();
        return now.getYear() == year && now.getMonthValue() == month;
    }

    /**
     * 从API获取7天预报数据作为参考
     */
    private List<WeatherData> getForecastDataForReference(String cityCode, String cityName) {
        try {
            // 先检查数据库中是否已有预报数据
            LocalDate today = LocalDate.now();
            List<WeatherData> existingData = weatherDataService.getHistoryData(cityCode, today, today.plusDays(7));
            if (existingData != null && existingData.size() >= 5) {
                return existingData;
            }

            // 从API获取7天预报
            List<WeatherData> forecast = weatherApiUtil.getForecast7Day(cityCode, cityName);
            if (forecast != null && !forecast.isEmpty()) {
                weatherDataService.saveOrUpdateData(forecast);
                return forecast;
            }
        } catch (Exception e) {
            log.warn("获取7天预报数据失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 基于7天预报数据计算统计
     */
    private WeatherStatistics calculateFromForecastData(String cityCode, String cityName, int year, int month, List<WeatherData> forecastData) {
        BigDecimal tempSum = BigDecimal.ZERO;
        BigDecimal maxTemp = new BigDecimal("-100");
        BigDecimal minTemp = new BigDecimal("100");
        BigDecimal humiditySum = BigDecimal.ZERO;
        BigDecimal precipSum = BigDecimal.ZERO;
        int rainDays = 0, sunnyDays = 0, cloudyDays = 0;
        int count = forecastData.size();

        for (WeatherData d : forecastData) {
            // 计算平均温度
            if (d.getTempMax() != null && d.getTempMin() != null) {
                BigDecimal avgTemp = d.getTempMax().add(d.getTempMin()).divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP);
                tempSum = tempSum.add(avgTemp);
            }

            if (d.getTempMax() != null && d.getTempMax().compareTo(maxTemp) > 0) {
                maxTemp = d.getTempMax();
            }
            if (d.getTempMin() != null && d.getTempMin().compareTo(minTemp) < 0) {
                minTemp = d.getTempMin();
            }
            if (d.getHumidity() != null) {
                humiditySum = humiditySum.add(new BigDecimal(d.getHumidity()));
            }
            if (d.getPrecip() != null) {
                precipSum = precipSum.add(d.getPrecip());
                if (d.getPrecip().doubleValue() > 0.1) rainDays++;
            }
            if (d.getWeatherText() != null) {
                if (d.getWeatherText().contains("晴")) sunnyDays++;
                else if (d.getWeatherText().contains("云") || d.getWeatherText().contains("阴")) cloudyDays++;
            }
        }

        // 基于7天数据推算整月数据
        int daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth();
        double scale = (double) daysInMonth / count;

        WeatherStatistics stats = new WeatherStatistics();
        stats.setCity(cityName);
        stats.setCityCode(cityCode);
        stats.setStatYear(year);
        stats.setStatMonth(month);
        stats.setAvgTemp(tempSum.divide(new BigDecimal(count), 1, RoundingMode.HALF_UP));
        stats.setMaxTemp(maxTemp);
        stats.setMinTemp(minTemp);
        stats.setAvgHumidity(humiditySum.divide(new BigDecimal(count), 1, RoundingMode.HALF_UP));
        stats.setTotalPrecip(precipSum.multiply(new BigDecimal(scale)).setScale(1, RoundingMode.HALF_UP));
        stats.setRainDays((int) Math.round(rainDays * scale));
        stats.setSunnyDays((int) Math.round(sunnyDays * scale));
        stats.setCloudyDays((int) Math.round(cloudyDays * scale));

        return stats;
    }

    /**
     * 基于真实数据计算统计
     */
    private WeatherStatistics calculateFromRealData(String cityCode, String cityName, int year, int month, List<WeatherData> dataList) {
        BigDecimal tempSum = BigDecimal.ZERO;
        BigDecimal maxTemp = new BigDecimal("-100");
        BigDecimal minTemp = new BigDecimal("100");
        BigDecimal humiditySum = BigDecimal.ZERO;
        BigDecimal precipSum = BigDecimal.ZERO;
        int rainDays = 0, sunnyDays = 0, cloudyDays = 0;
        int count = dataList.size();

        for (WeatherData d : dataList) {
            if (d.getTemp() != null) {
                tempSum = tempSum.add(d.getTemp());
            } else if (d.getTempMax() != null && d.getTempMin() != null) {
                tempSum = tempSum.add(d.getTempMax().add(d.getTempMin()).divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP));
            }

            if (d.getTempMax() != null && d.getTempMax().compareTo(maxTemp) > 0) {
                maxTemp = d.getTempMax();
            }
            if (d.getTempMin() != null && d.getTempMin().compareTo(minTemp) < 0) {
                minTemp = d.getTempMin();
            }
            if (d.getHumidity() != null) {
                humiditySum = humiditySum.add(new BigDecimal(d.getHumidity()));
            }
            if (d.getPrecip() != null) {
                precipSum = precipSum.add(d.getPrecip());
                if (d.getPrecip().doubleValue() > 0.1) rainDays++;
            }
            if (d.getWeatherText() != null) {
                if (d.getWeatherText().contains("晴")) sunnyDays++;
                else if (d.getWeatherText().contains("云") || d.getWeatherText().contains("阴")) cloudyDays++;
            }
        }

        WeatherStatistics stats = new WeatherStatistics();
        stats.setCity(cityName);
        stats.setCityCode(cityCode);
        stats.setStatYear(year);
        stats.setStatMonth(month);
        stats.setAvgTemp(tempSum.divide(new BigDecimal(count), 1, RoundingMode.HALF_UP));
        stats.setMaxTemp(maxTemp);
        stats.setMinTemp(minTemp);
        stats.setAvgHumidity(humiditySum.divide(new BigDecimal(count), 1, RoundingMode.HALF_UP));
        stats.setTotalPrecip(precipSum.setScale(1, RoundingMode.HALF_UP));
        stats.setRainDays(rainDays);
        stats.setSunnyDays(sunnyDays);
        stats.setCloudyDays(cloudyDays);

        return stats;
    }

    /**
     * 基于气候常理数据生成统计
     */
    private WeatherStatistics generateFromClimateNormal(String cityCode, String cityName, int year, int month) {
        double[][] climateData = CLIMATE_NORMALS.getOrDefault(cityName, CLIMATE_NORMALS.get("default"));
        double[] monthData = climateData[month - 1];

        Random random = new Random(cityCode.hashCode() + year * 100 + month);
        double variation = 0.1;

        double avgTemp = monthData[0] * (1 + (random.nextDouble() * 2 - 1) * variation);
        double maxTemp = monthData[1] * (1 + (random.nextDouble() * 2 - 1) * variation * 0.5);
        double minTemp = monthData[2] * (1 + (random.nextDouble() * 2 - 1) * variation * 0.5);
        double humidity = monthData[3] * (1 + (random.nextDouble() * 2 - 1) * variation * 0.3);
        double precip = monthData[4] * (1 + (random.nextDouble() * 2 - 1) * variation);
        int rainyDays = (int) Math.round(monthData[5] * (1 + (random.nextDouble() * 2 - 1) * variation * 0.5));
        int sunnyDays = (int) Math.round(monthData[6] * (1 + (random.nextDouble() * 2 - 1) * variation * 0.3));
        int cloudyDays = (int) Math.round(monthData[7] * (1 + (random.nextDouble() * 2 - 1) * variation * 0.3));

        int daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth();
        rainyDays = Math.min(rainyDays, daysInMonth);
        sunnyDays = Math.min(sunnyDays, daysInMonth);
        cloudyDays = Math.min(cloudyDays, daysInMonth);
        humidity = Math.min(100, Math.max(30, humidity));

        if (maxTemp < avgTemp + 3) maxTemp = avgTemp + 3 + random.nextDouble() * 3;
        if (minTemp > avgTemp - 3) minTemp = avgTemp - 3 - random.nextDouble() * 3;

        WeatherStatistics stats = new WeatherStatistics();
        stats.setCity(cityName);
        stats.setCityCode(cityCode);
        stats.setStatYear(year);
        stats.setStatMonth(month);
        stats.setAvgTemp(new BigDecimal(avgTemp).setScale(1, RoundingMode.HALF_UP));
        stats.setMaxTemp(new BigDecimal(maxTemp).setScale(1, RoundingMode.HALF_UP));
        stats.setMinTemp(new BigDecimal(minTemp).setScale(1, RoundingMode.HALF_UP));
        stats.setAvgHumidity(new BigDecimal(humidity).setScale(1, RoundingMode.HALF_UP));
        stats.setTotalPrecip(new BigDecimal(precip).setScale(1, RoundingMode.HALF_UP));
        stats.setRainDays(rainyDays);
        stats.setSunnyDays(sunnyDays);
        stats.setCloudyDays(cloudyDays);

        return stats;
    }

    @Override
    public List<WeatherStatistics> getCityStatistics(String cityCode, int year) {
        String key = statsCacheKey(cityCode, year);
        List<WeatherStatistics> cached = cache.get(key, List.class);
        if (cached != null) {
            return cached;
        }
        List<WeatherStatistics> stats = list(new LambdaQueryWrapper<WeatherStatistics>()
            .eq(WeatherStatistics::getCityCode, cityCode)
            .eq(WeatherStatistics::getStatYear, year)
            .orderByAsc(WeatherStatistics::getStatMonth));
        if (stats != null && !stats.isEmpty()) {
            cache.put(key, stats, STATS_TTL);
        }
        return stats;
    }

    @Override
    public int countRealDataDays(String cityCode, int year) {
        List<WeatherData> data = weatherDataService.getHistoryData(cityCode,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        if (data == null || data.isEmpty()) {
            return 0;
        }
        Set<LocalDate> days = new HashSet<>();
        for (WeatherData d : data) {
            if (d.getWeatherDate() != null) {
                days.add(d.getWeatherDate());
            }
        }
        return days.size();
    }
}
