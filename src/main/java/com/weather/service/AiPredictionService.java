package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.AiPrediction;
import java.util.List;

public interface AiPredictionService extends IService<AiPrediction> {
    List<AiPrediction> generatePrediction(String cityCode, String cityName);
    List<AiPrediction> getLatestPrediction(String cityCode);
}
