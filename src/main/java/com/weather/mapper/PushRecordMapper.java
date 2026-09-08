package com.weather.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weather.entity.PushRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PushRecordMapper extends BaseMapper<PushRecord> {
}
