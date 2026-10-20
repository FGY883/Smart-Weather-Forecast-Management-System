-- ============================================
-- 智能天气预报系统 数据库建表脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS weather_forecast DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE weather_forecast;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `nickname` VARCHAR(50) DEFAULT '' COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '手机号',
    `avatar` VARCHAR(255) DEFAULT '' COMMENT '头像URL',
    `city` VARCHAR(50) DEFAULT '' COMMENT '默认城市',
    `city_code` VARCHAR(20) DEFAULT '' COMMENT '城市编码',
    `push_enabled` TINYINT(1) DEFAULT 1 COMMENT '是否开启推送(0否1是)',
    `role` VARCHAR(20) DEFAULT 'user' COMMENT '角色(user/admin)',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态(0禁用1正常)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 天气数据表（历史+实时）
CREATE TABLE IF NOT EXISTS `weather_data` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `city` VARCHAR(50) NOT NULL COMMENT '城市名',
    `city_code` VARCHAR(20) NOT NULL COMMENT '城市编码',
    `weather_date` DATE NOT NULL COMMENT '天气日期',
    `weather_text` VARCHAR(50) DEFAULT '' COMMENT '天气状况',
    `temp` DECIMAL(5,1) DEFAULT NULL COMMENT '温度(℃)',
    `temp_max` DECIMAL(5,1) DEFAULT NULL COMMENT '最高温度',
    `temp_min` DECIMAL(5,1) DEFAULT NULL COMMENT '最低温度',
    `feels_like` DECIMAL(5,1) DEFAULT NULL COMMENT '体感温度',
    `humidity` INT DEFAULT NULL COMMENT '湿度(%)',
    `wind_dir` VARCHAR(20) DEFAULT '' COMMENT '风向',
    `wind_speed` DECIMAL(5,1) DEFAULT NULL COMMENT '风速(km/h)',
    `wind_scale` VARCHAR(10) DEFAULT '' COMMENT '风力等级',
    `pressure` INT DEFAULT NULL COMMENT '气压(hPa)',
    `visibility` DECIMAL(5,1) DEFAULT NULL COMMENT '能见度(km)',
    `uv_index` INT DEFAULT NULL COMMENT '紫外线指数',
    `precip` DECIMAL(5,1) DEFAULT NULL COMMENT '降水量(mm)',
    `data_type` TINYINT DEFAULT 1 COMMENT '数据类型(1实况 2预报 3历史)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_city_date_type` (`city_code`, `weather_date`, `data_type`),
    KEY `idx_weather_date` (`weather_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气数据表';

-- 3. AI预测结果表
CREATE TABLE IF NOT EXISTS `ai_prediction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `city` VARCHAR(50) NOT NULL COMMENT '城市名',
    `city_code` VARCHAR(20) NOT NULL COMMENT '城市编码',
    `predict_date` DATE NOT NULL COMMENT '预测日期',
    `target_date` DATE NOT NULL COMMENT '目标日期',
    `temp_max_pred` DECIMAL(5,1) DEFAULT NULL COMMENT '预测最高温度',
    `temp_min_pred` DECIMAL(5,1) DEFAULT NULL COMMENT '预测最低温度',
    `weather_pred` VARCHAR(50) DEFAULT '' COMMENT '预测天气状况',
    `humidity_pred` INT DEFAULT NULL COMMENT '预测湿度',
    `precip_pred` DECIMAL(5,1) DEFAULT NULL COMMENT '预测降水量',
    `confidence` DECIMAL(5,2) DEFAULT NULL COMMENT '预测置信度(0-1)',
    `algorithm` VARCHAR(50) DEFAULT 'linear_regression' COMMENT '预测算法',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_city_predict_target` (`city_code`, `predict_date`, `target_date`),
    KEY `idx_city_target` (`city_code`, `target_date`),
    KEY `idx_city_predict` (`city_code`, `predict_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI预测结果表';

-- 4. 极端天气预警表
CREATE TABLE IF NOT EXISTS `weather_warning` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `city` VARCHAR(50) NOT NULL COMMENT '城市名',
    `city_code` VARCHAR(20) NOT NULL COMMENT '城市编码',
    `warning_type` VARCHAR(30) NOT NULL COMMENT '预警类型(HEAVY_RAIN/HIGH_TEMP/COLD_WAVE/STRONG_WIND)',
    `warning_level` VARCHAR(20) NOT NULL COMMENT '预警等级(BLUE/YELLOW/ORANGE/RED)',
    `warning_title` VARCHAR(100) DEFAULT '' COMMENT '预警标题',
    `warning_content` TEXT COMMENT '预警内容',
    `start_time` DATETIME DEFAULT NULL COMMENT '预警开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '预警结束时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态(0已解除1生效中)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_city_status` (`city_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='极端天气预警表';

-- 5. 用户预警通知表（预留：当前预警通过收藏城市实时查询展示，尚未做"逐用户落库通知+已读"链路）
CREATE TABLE IF NOT EXISTS `user_warning_notice` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `warning_id` BIGINT NOT NULL COMMENT '预警ID',
    `is_read` TINYINT(1) DEFAULT 0 COMMENT '是否已读(0否1是)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户预警通知表';

-- 6. 天气收藏表
CREATE TABLE IF NOT EXISTS `weather_favorite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `city` VARCHAR(50) NOT NULL COMMENT '城市名',
    `city_code` VARCHAR(20) NOT NULL COMMENT '城市编码',
    `remark` VARCHAR(100) DEFAULT '' COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_city` (`user_id`, `city_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气收藏表';

-- 7. 个性化推送记录表
CREATE TABLE IF NOT EXISTS `push_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `push_type` VARCHAR(30) NOT NULL COMMENT '推送类型(DAILY_WEATHER/WARNING/TREND)',
    `push_content` TEXT COMMENT '推送内容',
    `push_status` TINYINT DEFAULT 0 COMMENT '推送状态(0未推送1已推送)',
    `push_time` DATETIME DEFAULT NULL COMMENT '推送时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送记录表';

-- 8. 天气统计表（按月/按城市聚合）
CREATE TABLE IF NOT EXISTS `weather_statistics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `city` VARCHAR(50) NOT NULL COMMENT '城市名',
    `city_code` VARCHAR(20) NOT NULL COMMENT '城市编码',
    `stat_year` INT NOT NULL COMMENT '统计年份',
    `stat_month` INT NOT NULL COMMENT '统计月份',
    `avg_temp` DECIMAL(5,1) DEFAULT NULL COMMENT '平均温度',
    `max_temp` DECIMAL(5,1) DEFAULT NULL COMMENT '最高温度',
    `min_temp` DECIMAL(5,1) DEFAULT NULL COMMENT '最低温度',
    `avg_humidity` DECIMAL(5,1) DEFAULT NULL COMMENT '平均湿度',
    `total_precip` DECIMAL(8,1) DEFAULT NULL COMMENT '总降水量',
    `rain_days` INT DEFAULT 0 COMMENT '降雨天数',
    `sunny_days` INT DEFAULT 0 COMMENT '晴天天数',
    `cloudy_days` INT DEFAULT 0 COMMENT '多云天数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_city_month` (`city_code`, `stat_year`, `stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='天气统计表';

-- 插入管理员账号（密码: admin123, BCrypt加密）
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `status`)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin', 1);

-- 插入测试用户（密码: user123）
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `city`, `city_code`, `role`, `status`)
VALUES ('testuser', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36PQm6PZ1YHFLNMGD1XSmJG', '测试用户', '北京', '101010100', 'user', 1);
