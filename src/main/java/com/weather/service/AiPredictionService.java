package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.AiPrediction;
import java.util.List;
import java.util.Map;

public interface AiPredictionService extends IService<AiPrediction> {
    List<AiPrediction> generatePrediction(String cityCode, String cityName);
    List<AiPrediction> getLatestPrediction(String cityCode);

    /** 预测回测可靠性指标（AI vs 持久性 vs 官方），供预报页诚实呈现；数据不足时 available=false。 */
    Map<String, Object> getAccuracy(String cityCode);
}
