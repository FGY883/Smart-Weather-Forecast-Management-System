package com.weather.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.weather.entity.PushRecord;
import java.util.List;

public interface PushRecordService extends IService<PushRecord> {
    void pushDailyWeather(Long userId, String content);
    void pushWarning(Long userId, String content);
    List<PushRecord> getUserPushRecords(Long userId);
}
