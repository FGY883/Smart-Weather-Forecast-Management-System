package com.weather.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weather.entity.WeatherData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WeatherDataMapper extends BaseMapper<WeatherData> {

    @Select("SELECT * FROM weather_data WHERE city_code = #{cityCode} AND weather_date BETWEEN #{start} AND #{end} ORDER BY weather_date")
    List<WeatherData> selectByCityAndDateRange(@Param("cityCode") String cityCode,
                                                @Param("start") LocalDate start,
                                                @Param("end") LocalDate end);

    @Select("SELECT * FROM weather_data WHERE city_code = #{cityCode} AND data_type = 1 ORDER BY weather_date DESC LIMIT #{limit}")
    List<WeatherData> selectRecentHistory(@Param("cityCode") String cityCode, @Param("limit") int limit);
}
