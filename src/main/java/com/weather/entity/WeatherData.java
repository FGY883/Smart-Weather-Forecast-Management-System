package com.weather.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("weather_data")
public class WeatherData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String city;
    private String cityCode;
    private LocalDate weatherDate;
    private String weatherText;
    private BigDecimal temp;
    private BigDecimal tempMax;
    private BigDecimal tempMin;
    private BigDecimal feelsLike;
    private Integer humidity;
    private String windDir;
    private BigDecimal windSpeed;
    private String windScale;
    private Integer pressure;
    private BigDecimal visibility;
    private Integer uvIndex;
    private BigDecimal precip;
    private Integer dataType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
