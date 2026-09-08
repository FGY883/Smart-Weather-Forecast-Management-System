package com.weather.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("weather_warning")
public class WeatherWarning {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String city;
    private String cityCode;
    private String warningType;
    private String warningLevel;
    private String warningTitle;
    private String warningContent;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
