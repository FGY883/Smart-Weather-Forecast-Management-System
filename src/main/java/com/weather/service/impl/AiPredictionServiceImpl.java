package com.weather.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.AiPrediction;
import com.weather.entity.WeatherData;
import com.weather.mapper.AiPredictionMapper;
import com.weather.service.AiPredictionService;
import com.weather.service.WeatherDataService;
import com.weather.util.AiPredictionUtil;
import com.weather.util.WeatherApiUtil;
import com.weather.util.WeatherCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class AiPredictionServiceImpl extends ServiceImpl<AiPredictionMapper, AiPrediction> implements AiPredictionService {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionServiceImpl.class);

    @Autowired
    private WeatherDataService weatherDataService;

    @Autowired
    private AiPredictionUtil aiPredictionUtil;

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private WeatherCache cache;

    /** 同一城市当天只生成一次预测，避免每次访问都触发 30 次历史天气接口 */
    private static final Duration PREDICTION_TTL = Duration.ofHours(6);

    @Value("${ai.prediction.history-days:7}")
    private int historyDays;

    @Value("${ai.prediction.forecast-days:7}")
    private int forecastDays;

    @Override
    public List<AiPrediction> generatePrediction(String cityCode, String cityName) {
        LocalDate today = LocalDate.now();
        String cacheKey = "ai:" + cityCode + ":" + today;

        // 当天已经算过就直接复用，历史天气接口按天查询，重复生成代价很高
        List<AiPrediction> cached = cache.get(cacheKey, List.class);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 删除该城市今天的旧预测数据
        remove(new LambdaQueryWrapper<AiPrediction>()
            .eq(AiPrediction::getCityCode, cityCode)
            .eq(AiPrediction::getPredictDate, today));

        // 1. 从数据库查询近30天数据，并按天聚合：
        //    库中同一天可能同时存在实时行（tempMax/tempMin 为 NULL）和多条重复预报行，
        //    直接用于回归会把 NULL 映射成 0，严重拉偏预测值，必须先聚合去重。
        LocalDate endDate = today.minusDays(1);
        LocalDate startDate = endDate.minusDays(historyDays - 1);
        List<WeatherData> rawData = weatherDataService.getHistoryData(cityCode, startDate, endDate);
        Map<LocalDate, WeatherData> byDay = aggregateByDay(rawData);

        // 2. 库内历史不足30天时，调用和风天气历史接口补齐（修复后才能真正拿到30天历史）
        if (byDay.size() < historyDays) {
            log.info("数据库历史数据仅{}天，调用历史天气接口补齐...", byDay.size());
            try {
                List<WeatherData> history = weatherApiUtil.getHistoricalWeather(cityCode, cityName, historyDays);
                if (history != null && !history.isEmpty()) {
                    List<WeatherData> toSave = new ArrayList<>();
                    for (WeatherData d : history) {
                        if (d.getWeatherDate() != null && !byDay.containsKey(d.getWeatherDate())) {
                            byDay.put(d.getWeatherDate(), d);
                            toSave.add(d);
                        }
                    }
                    if (!toSave.isEmpty()) {
                        weatherDataService.saveOrUpdateData(toSave);
                    }
                    log.info("历史接口补齐{}天数据", toSave.size());
                }
            } catch (Exception e) {
                log.warn("历史天气接口调用失败: {}", e.getMessage());
            }
        }

        List<WeatherData> historyData = new ArrayList<>(byDay.values());

        // 3. 若仍没有足够数据（如接口不可用的新城市），退而使用7天预报作为参考序列
        if (historyData.size() < 3) {
            log.info("历史数据不足，从和风天气API获取7天预报数据作为参考...");
            List<WeatherData> forecastData = weatherApiUtil.getForecast7Day(cityCode, cityName);
            if (forecastData != null && !forecastData.isEmpty()) {
                weatherDataService.saveOrUpdateData(forecastData);
                historyData = new ArrayList<>(aggregateByDay(forecastData).values());
            }
        }

        // 4. 实在没有数据时使用默认预测
        if (historyData == null || historyData.size() < 3) {
            log.warn("历史数据不足，使用默认预测");
            return generateDefaultPrediction(cityCode, cityName);
        }
        // 使用AI算法进行预测
        List<BigDecimal> tempMaxPred = aiPredictionUtil.predictTempMax(historyData, forecastDays);
        List<BigDecimal> tempMinPred = aiPredictionUtil.predictTempMin(historyData, forecastDays);
        List<Integer> humidityPred = aiPredictionUtil.predictHumidity(historyData, forecastDays);
        List<BigDecimal> precipPred = aiPredictionUtil.predictPrecip(historyData, forecastDays);
        BigDecimal confidence = aiPredictionUtil.calculateConfidence(
            historyData.stream().mapToDouble(d -> d.getTempMax() != null
                    ? d.getTempMax().doubleValue()
                    : (d.getTemp() != null ? d.getTemp().doubleValue() : 0)).toArray()
        );

        List<AiPrediction> predictions = new ArrayList<>();

        for (int i = 0; i < forecastDays; i++) {
            // 保证最低温不高于最高温
            BigDecimal maxT = tempMaxPred.get(i);
            BigDecimal minT = tempMinPred.get(i);
            if (maxT.compareTo(minT) < 0) {
                BigDecimal tmp = maxT;
                maxT = minT;
                minT = tmp;
            }

            AiPrediction pred = new AiPrediction();
            pred.setCity(cityName);
            pred.setCityCode(cityCode);
            pred.setPredictDate(today);
            pred.setTargetDate(today.plusDays(i + 1));
            pred.setTempMaxPred(maxT);
            pred.setTempMinPred(minT);
            pred.setHumidityPred(humidityPred.get(i));
            pred.setPrecipPred(precipPred.get(i));
            pred.setWeatherPred(aiPredictionUtil.predictWeatherText(
                maxT, minT, precipPred.get(i), humidityPred.get(i)
            ));
            pred.setConfidence(confidence);
            pred.setAlgorithm("linear_regression_moving_avg");
            predictions.add(pred);
        }

        saveBatch(predictions);
        cache.put(cacheKey, predictions, PREDICTION_TTL);
        return predictions;
    }

    /**
     * 把原始天气记录按天聚合：同一天取最高温的最大值、最低温的最小值、
     * 湿度平均值、降水量最大值，并优先保留预报/历史行的天气文本。
     * 解决实时行 tempMax/tempMin 为 NULL、同一天多条重复行直接喂给回归导致预测失真的问题。
     */
    private Map<LocalDate, WeatherData> aggregateByDay(List<WeatherData> rawData) {
        Map<LocalDate, WeatherData> result = new TreeMap<>();
        Map<LocalDate, double[]> acc = new HashMap<>(); // [humiditySum, humidityCount, precipMax]
        if (rawData == null) {
            return result;
        }
        for (WeatherData d : rawData) {
            LocalDate date = d.getWeatherDate();
            if (date == null) continue;

            WeatherData agg = result.computeIfAbsent(date, k -> {
                WeatherData a = new WeatherData();
                a.setCity(d.getCity());
                a.setCityCode(d.getCityCode());
                a.setWeatherDate(date);
                return a;
            });
            double[] a = acc.computeIfAbsent(date, k -> new double[3]);

            // 最高温：取 tempMax 与 temp 中的较大者（实时行 tempMax 为 NULL 时用 temp 补齐）
            BigDecimal dayMax = maxOf(d.getTempMax(), d.getTemp());
            if (dayMax != null && (agg.getTempMax() == null || dayMax.compareTo(agg.getTempMax()) > 0)) {
                agg.setTempMax(dayMax);
            }
            // 最低温：取 tempMin 与 temp 中的较小者
            BigDecimal dayMin = minOf(d.getTempMin(), d.getTemp());
            if (dayMin != null && (agg.getTempMin() == null || dayMin.compareTo(agg.getTempMin()) < 0)) {
                agg.setTempMin(dayMin);
            }
            // 湿度：按天取平均
            if (d.getHumidity() != null) {
                a[0] += d.getHumidity();
                a[1] += 1;
                agg.setHumidity((int) Math.round(a[0] / a[1]));
            }
            // 降水量：按天取最大
            if (d.getPrecip() != null && d.getPrecip().doubleValue() > a[2]) {
                a[2] = d.getPrecip().doubleValue();
                agg.setPrecip(d.getPrecip());
            }
            // 天气文本：预报行(data_type=2)/历史行(data_type=3)优先于实时行
            if (d.getWeatherText() != null && !d.getWeatherText().isEmpty()
                    && (agg.getWeatherText() == null
                        || (d.getDataType() != null && d.getDataType() >= 2))) {
                agg.setWeatherText(d.getWeatherText());
            }
            // 平均气温 = (最高+最低)/2
            if (agg.getTempMax() != null && agg.getTempMin() != null) {
                agg.setTemp(agg.getTempMax().add(agg.getTempMin())
                    .divide(new BigDecimal("2"), 1, RoundingMode.HALF_UP));
            }
        }
        return result;
    }

    private BigDecimal maxOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    private BigDecimal minOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }

    @Override
    public List<AiPrediction> getLatestPrediction(String cityCode) {
        LocalDate today = LocalDate.now();
        return list(new LambdaQueryWrapper<AiPrediction>()
            .eq(AiPrediction::getCityCode, cityCode)
            .eq(AiPrediction::getPredictDate, today)
            .orderByAsc(AiPrediction::getTargetDate));
    }

    private List<AiPrediction> generateDefaultPrediction(String cityCode, String cityName) {
        List<AiPrediction> predictions = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < forecastDays; i++) {
            AiPrediction pred = new AiPrediction();
            pred.setCity(cityName);
            pred.setCityCode(cityCode);
            pred.setPredictDate(today);
            pred.setTargetDate(today.plusDays(i + 1));
            pred.setTempMaxPred(new BigDecimal("25.0"));
            pred.setTempMinPred(new BigDecimal("15.0"));
            pred.setHumidityPred(60);
            pred.setPrecipPred(BigDecimal.ZERO);
            pred.setWeatherPred("多云");
            pred.setConfidence(new BigDecimal("0.50"));
            pred.setAlgorithm("default");
            predictions.add(pred);
        }
        saveBatch(predictions);
        return predictions;
    }
}
