package com.weather.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.weather.entity.PushRecord;
import com.weather.mapper.PushRecordMapper;
import com.weather.service.PushRecordService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PushRecordServiceImpl extends ServiceImpl<PushRecordMapper, PushRecord> implements PushRecordService {

    @Override
    public void pushDailyWeather(Long userId, String content) {
        PushRecord record = new PushRecord();
        record.setUserId(userId);
        record.setPushType("DAILY_WEATHER");
        record.setPushContent(content);
        record.setPushStatus(1);
        record.setPushTime(LocalDateTime.now());
        save(record);
    }

    @Override
    public void pushWarning(Long userId, String content) {
        PushRecord record = new PushRecord();
        record.setUserId(userId);
        record.setPushType("WARNING");
        record.setPushContent(content);
        record.setPushStatus(1);
        record.setPushTime(LocalDateTime.now());
        save(record);
    }

    @Override
    public List<PushRecord> getUserPushRecords(Long userId) {
        return getUserPushRecords(userId, 50);
    }

    @Override
    public List<PushRecord> getUserPushRecords(Long userId, int limit) {
        return list(new LambdaQueryWrapper<PushRecord>()
            .eq(PushRecord::getUserId, userId)
            .orderByDesc(PushRecord::getCreateTime)
            .last(Math.max(0, limit) > 0 ? "LIMIT " + limit : ""));
    }
}
