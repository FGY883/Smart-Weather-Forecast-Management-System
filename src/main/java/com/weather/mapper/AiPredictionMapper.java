package com.weather.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weather.entity.AiPrediction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPredictionMapper extends BaseMapper<AiPrediction> {
}
