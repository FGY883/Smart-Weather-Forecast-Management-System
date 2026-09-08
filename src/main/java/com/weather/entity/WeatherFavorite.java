package com.weather.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("weather_favorite")
public class WeatherFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String city;
    private String cityCode;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
