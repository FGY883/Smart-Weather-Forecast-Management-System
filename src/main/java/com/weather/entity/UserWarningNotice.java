package com.weather.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_warning_notice")
public class UserWarningNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long warningId;
    private Boolean isRead;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
