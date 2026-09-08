package com.weather.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weather.entity.WeatherWarning;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeatherWarningMapper extends BaseMapper<WeatherWarning> {
}
