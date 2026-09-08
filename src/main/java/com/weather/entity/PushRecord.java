package com.weather.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("push_record")
public class PushRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String pushType;
    private String pushContent;
    private Integer pushStatus;
    private LocalDateTime pushTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
