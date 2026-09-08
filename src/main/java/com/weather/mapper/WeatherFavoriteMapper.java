package com.weather.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weather.entity.WeatherFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeatherFavoriteMapper extends BaseMapper<WeatherFavorite> {
}
