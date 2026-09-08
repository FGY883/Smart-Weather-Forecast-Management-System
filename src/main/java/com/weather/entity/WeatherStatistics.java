package com.weather.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("weather_statistics")
public class WeatherStatistics {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String city;
    private String cityCode;
    private Integer statYear;
    private Integer statMonth;
    private BigDecimal avgTemp;
    private BigDecimal maxTemp;
    private BigDecimal minTemp;
    private BigDecimal avgHumidity;
    private BigDecimal totalPrecip;
    private Integer rainDays;
    private Integer sunnyDays;
    private Integer cloudyDays;
    @TableField(fill = FieldFill.INSERT)
    private java.time.LocalDateTime createTime;
}
