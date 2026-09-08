package com.weather.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_prediction")
public class AiPrediction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String city;
    private String cityCode;
    private LocalDate predictDate;
    private LocalDate targetDate;
    private BigDecimal tempMaxPred;
    private BigDecimal tempMinPred;
    private String weatherPred;
    private Integer humidityPred;
    private BigDecimal precipPred;
    private BigDecimal confidence;
    private String algorithm;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
