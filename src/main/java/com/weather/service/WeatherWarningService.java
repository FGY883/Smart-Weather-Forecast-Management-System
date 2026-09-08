package com.weather.service;

import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.WeatherWarning;
import java.util.List;

public interface WeatherWarningService extends IService<WeatherWarning> {
    List<WeatherWarning> checkAndGenerateWarnings(String cityCode, String cityName);
    List<WeatherWarning> getActiveWarnings(String cityCode);
    List<WeatherWarning> getAllActiveWarnings();

    /**
     * 解除已过期的预警（end_time 早于当前时间）。
     * 预警生成后不会自动消失，必须定期调用，否则列表会无限堆积。
     */
    int expireOutdatedWarnings();

    /**
     * 直接拉取和风天气灾害预警 API，不再走本地阈值合成。
     * 拿到原始 JSON 给模板渲染（或在落库前做二次处理）。
     */
    JSONArray fetchWarningsFromApi(String cityCode);

    /**
     * 全国正在生效预警的城市列表，用于预警列表页顶部概览。
     */
    JSONArray fetchAllWarningsFromApi();
}
