package com.weather.util;

import com.weather.entity.WeatherData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * AI天气预测工具类
 * 实现线性回归、移动平均等算法进行天气趋势预测
 */
@Component
public class AiPredictionUtil {

    /**
     * 线性回归预测 - 基于历史数据预测未来温度趋势
     * 使用最小二乘法拟合线性模型 y = ax + b
     */
    public double[] linearRegression(double[] x, double[] y) {
        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }
        double a = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double b = (sumY - a * sumX) / n;
        return new double[]{a, b};
    }

    /**
     * 移动平均预测 - 用于平滑短期波动
     */
    public double movingAverage(double[] data, int window) {
        if (data.length < window) {
            double sum = 0;
            for (double v : data) sum += v;
            return sum / data.length;
        }
        double sum = 0;
        for (int i = data.length - window; i < data.length; i++) {
            sum += data[i];
        }
        return sum / window;
    }

    /**
     * 加权移动平均 - 近期数据权重更大
     */
    public double weightedMovingAverage(double[] data, int window) {
        int len = Math.min(data.length, window);
        double sum = 0, weightSum = 0;
        for (int i = data.length - len; i < data.length; i++) {
            double weight = (i - (data.length - len) + 1);
            sum += data[i] * weight;
            weightSum += weight;
        }
        return sum / weightSum;
    }

    /**
     * 计算置信度 - 基于数据方差
     */
    public BigDecimal calculateConfidence(double[] data) {
        if (data.length < 3) return new BigDecimal("0.50");
        double mean = 0;
        for (double v : data) mean += v;
        mean /= data.length;
        double variance = 0;
        for (double v : data) variance += (v - mean) * (v - mean);
        variance /= data.length;
        double stdDev = Math.sqrt(variance);
        double confidence = Math.max(0.5, 1.0 - stdDev / 20.0);
        return new BigDecimal(confidence).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 预测未来N天最高温度（tempMax 缺失时回退到当天均温，过滤完全无温度的行）
     */
    public List<BigDecimal> predictTempMax(List<WeatherData> historyData, int days) {
        double[] temps = historyData.stream()
            .filter(d -> d.getTempMax() != null || d.getTemp() != null)
            .mapToDouble(d -> d.getTempMax() != null ? d.getTempMax().doubleValue() : d.getTemp().doubleValue())
            .toArray();
        return predictValues(temps, days);
    }

    /**
     * 预测未来N天最低温度（tempMin 缺失时回退到当天均温，过滤完全无温度的行）
     */
    public List<BigDecimal> predictTempMin(List<WeatherData> historyData, int days) {
        double[] temps = historyData.stream()
            .filter(d -> d.getTempMin() != null || d.getTemp() != null)
            .mapToDouble(d -> d.getTempMin() != null ? d.getTempMin().doubleValue() : d.getTemp().doubleValue())
            .toArray();
        return predictValues(temps, days);
    }

    /**
     * 预测未来N天湿度
     */
    public List<Integer> predictHumidity(List<WeatherData> historyData, int days) {
        double[] humidity = historyData.stream()
            .mapToDouble(d -> d.getHumidity() != null ? d.getHumidity() : 50)
            .toArray();
        List<BigDecimal> pred = predictValues(humidity, days);
        List<Integer> result = new ArrayList<>();
        for (BigDecimal v : pred) {
            int val = v.intValue();
            result.add(Math.max(0, Math.min(100, val)));
        }
        return result;
    }

    /**
     * 预测未来N天降水量
     */
    public List<BigDecimal> predictPrecip(List<WeatherData> historyData, int days) {
        double[] precip = historyData.stream()
            .mapToDouble(d -> d.getPrecip() != null ? d.getPrecip().doubleValue() : 0)
            .toArray();
        List<BigDecimal> result = predictValues(precip, days);
        result.replaceAll(v -> v.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : v);
        return result;
    }

    /**
     * 综合预测 - 使用线性回归+移动平均融合算法
     *
     * 1. 最小二乘线性回归拟合 30 天序列的总体趋势；
     * 2. 斜率限幅 ±0.5/天——天气过程不会无限线性漂移，防止外推发散；
     * 3. 回归外推对序列均值的偏离量按 0.88^i 阻尼衰减（均值回复），
     *    预测越远越向气候均值回归；
     * 4. 与近 7 天加权移动平均按 6:4 融合，锚定当前天气水平；
     * 5. 输出限制在历史观测区间 ±4 内，杜绝荒谬值。
     */
    private List<BigDecimal> predictValues(double[] history, int days) {
        List<BigDecimal> predictions = new ArrayList<>();
        if (history.length < 3) {
            double avg = 0;
            for (double v : history) avg += v;
            avg = history.length > 0 ? avg / history.length : 0;
            for (int i = 0; i < days; i++) {
                predictions.add(new BigDecimal(avg).setScale(1, RoundingMode.HALF_UP));
            }
            return predictions;
        }

        int n = history.length;
        double mean = 0;
        double min = history[0], max = history[0];
        for (double v : history) {
            mean += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        mean /= n;

        // 1. 线性回归拟合总体趋势
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = i;
        double[] ab = linearRegression(x, history);

        // 2. 斜率限幅：防止短期噪声造成的外推漂移
        ab[0] = Math.max(-0.5, Math.min(0.5, ab[0]));

        // 3. 加权移动平均刻画当前天气水平
        double level = weightedMovingAverage(history, Math.min(7, n));

        double lower = min - 4;
        double upper = max + 4;
        double damp = 0.88;

        for (int i = 1; i <= days; i++) {
            // 回归外推，并向序列均值做阻尼回拉
            double reg = ab[0] * (n - 1 + i) + ab[1];
            double reverted = mean + (reg - mean) * Math.pow(damp, i);
            // 与近期水平融合（60% 回归 + 40% 加权移动平均）
            double fused = 0.6 * reverted + 0.4 * level;
            // 限制在合理范围内
            fused = Math.max(lower, Math.min(upper, fused));
            predictions.add(new BigDecimal(fused).setScale(1, RoundingMode.HALF_UP));
        }
        return predictions;
    }

    /**
     * 根据预测温度和湿度判断天气状况
     */
    public String predictWeatherText(BigDecimal tempMax, BigDecimal tempMin, BigDecimal precip, Integer humidity) {
        if (precip != null && precip.doubleValue() > 25) return "暴雨";
        if (precip != null && precip.doubleValue() > 10) return "大雨";
        if (precip != null && precip.doubleValue() > 5) return "中雨";
        if (precip != null && precip.doubleValue() > 0.5) return "小雨";
        if (humidity != null && humidity > 85) return "阴";
        if (humidity != null && humidity > 60) return "多云";
        return "晴";
    }
}
