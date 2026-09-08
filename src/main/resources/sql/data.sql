-- ============================================
-- 测试数据初始化脚本（可重复执行，不会产生重复数据）
--
-- 说明：本项目 spring.sql.init.mode=always，每次启动都会执行本脚本。
-- 对于没有唯一约束的表（weather_data / ai_prediction / weather_warning /
-- push_record / user_warning_notice），简单的 INSERT IGNORE 无法阻止重复，
-- 会导致每次启动都多插入一批种子数据。因此这里统一使用
-- INSERT ... SELECT ... WHERE NOT EXISTS 的写法做幂等保护。
-- 已有唯一约束的表（weather_favorite / weather_statistics）仍使用 INSERT IGNORE。
-- ============================================

USE weather_forecast;

-- 1. 天气数据（7天预报数据）
INSERT INTO `weather_data` (`city`, `city_code`, `weather_date`, `weather_text`, `temp`, `temp_max`, `temp_min`, `feels_like`, `humidity`, `wind_dir`, `wind_speed`, `wind_scale`, `pressure`, `visibility`, `uv_index`, `precip`, `data_type`)
SELECT * FROM (
    SELECT '北京', '101010100', CURDATE(), '晴', 28.5, 32.0, 24.0, 30.0, 45, '东南风', 12.5, '3级', 1013, 15.0, 7, 0.0, 2
    UNION ALL SELECT '北京', '101010100', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '多云', 27.0, 31.0, 23.0, 28.5, 52, '南风', 10.2, '3级', 1012, 12.0, 5, 0.0, 2
    UNION ALL SELECT '北京', '101010100', DATE_ADD(CURDATE(), INTERVAL 2 DAY), '小雨', 24.5, 28.0, 21.0, 25.0, 68, '西南风', 15.0, '4级', 1010, 8.0, 3, 5.2, 2
    UNION ALL SELECT '北京', '101010100', DATE_ADD(CURDATE(), INTERVAL 3 DAY), '阴', 25.0, 29.0, 22.0, 26.5, 60, '东风', 8.5, '2级', 1011, 10.0, 4, 0.0, 2
    UNION ALL SELECT '北京', '101010100', DATE_ADD(CURDATE(), INTERVAL 4 DAY), '晴', 30.0, 34.0, 26.0, 32.0, 42, '北风', 6.0, '2级', 1014, 18.0, 8, 0.0, 2
    UNION ALL SELECT '北京', '101010100', DATE_ADD(CURDATE(), INTERVAL 5 DAY), '晴转多云', 29.5, 33.5, 25.5, 31.0, 48, '南风', 9.0, '2级', 1013, 15.0, 7, 0.0, 2
    UNION ALL SELECT '北京', '101010100', DATE_ADD(CURDATE(), INTERVAL 6 DAY), '雷阵雨', 26.0, 30.0, 23.0, 27.5, 72, '西南风', 18.5, '5级', 1009, 6.0, 2, 15.8, 2
) AS s
WHERE NOT EXISTS (
    SELECT 1 FROM `weather_data` w
    WHERE w.`city_code` = '101010100' AND w.`weather_date` = CURDATE() AND w.`data_type` = 2
);

-- 2. AI预测结果
INSERT INTO `ai_prediction` (`city`, `city_code`, `predict_date`, `target_date`, `temp_max_pred`, `temp_min_pred`, `weather_pred`, `humidity_pred`, `precip_pred`, `confidence`, `algorithm`)
SELECT * FROM (
    SELECT '北京', '101010100', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), 31.5, 23.5, '多云', 50, 0.0, 0.85, 'linear_regression_moving_avg'
    UNION ALL SELECT '北京', '101010100', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 2 DAY), 28.5, 21.5, '小雨', 65, 8.5, 0.72, 'linear_regression_moving_avg'
    UNION ALL SELECT '北京', '101010100', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 3 DAY), 29.0, 22.0, '阴', 58, 2.0, 0.68, 'linear_regression_moving_avg'
    UNION ALL SELECT '北京', '101010100', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 4 DAY), 33.5, 25.5, '晴', 45, 0.0, 0.78, 'linear_regression_moving_avg'
    UNION ALL SELECT '北京', '101010100', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 5 DAY), 32.0, 24.5, '多云', 50, 0.0, 0.75, 'linear_regression_moving_avg'
    UNION ALL SELECT '北京', '101010100', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 6 DAY), 30.5, 23.0, '雷阵雨', 70, 18.0, 0.65, 'linear_regression_moving_avg'
    UNION ALL SELECT '北京', '101010100', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 31.0, 24.0, '晴', 48, 0.0, 0.70, 'linear_regression_moving_avg'
) AS s
WHERE NOT EXISTS (
    SELECT 1 FROM `ai_prediction` a
    WHERE a.`city_code` = '101010100' AND a.`predict_date` = CURDATE()
);

-- 3. 极端天气预警示例（按 城市+预警类型 去重）
INSERT INTO `weather_warning` (`city`, `city_code`, `warning_type`, `warning_level`, `warning_title`, `warning_content`, `start_time`, `end_time`, `status`)
SELECT * FROM (
    SELECT '北京', '101010100', 'HIGH_TEMP', 'YELLOW', '北京市高温黄色预警', '预计今日最高气温将达35℃以上，请注意防暑降温，减少户外活动。', NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), 1
) AS s
WHERE NOT EXISTS (SELECT 1 FROM `weather_warning` w WHERE w.`city_code` = '101010100' AND w.`warning_type` = 'HIGH_TEMP');

INSERT INTO `weather_warning` (`city`, `city_code`, `warning_type`, `warning_level`, `warning_title`, `warning_content`, `start_time`, `end_time`, `status`)
SELECT * FROM (
    SELECT '上海', '101020100', 'HEAVY_RAIN', 'ORANGE', '上海市暴雨橙色预警', '预计未来6小时内降雨量将达50毫米以上，请注意防范城市内涝。', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 1
) AS s
WHERE NOT EXISTS (SELECT 1 FROM `weather_warning` w WHERE w.`city_code` = '101020100' AND w.`warning_type` = 'HEAVY_RAIN');

INSERT INTO `weather_warning` (`city`, `city_code`, `warning_type`, `warning_level`, `warning_title`, `warning_content`, `start_time`, `end_time`, `status`)
SELECT * FROM (
    SELECT '广州', '101280101', 'STRONG_WIND', 'YELLOW', '广州市大风黄色预警', '预计今日将出现8级以上大风，请注意加固临时搭建物。', NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), 1
) AS s
WHERE NOT EXISTS (SELECT 1 FROM `weather_warning` w WHERE w.`city_code` = '101280101' AND w.`warning_type` = 'STRONG_WIND');

INSERT INTO `weather_warning` (`city`, `city_code`, `warning_type`, `warning_level`, `warning_title`, `warning_content`, `start_time`, `end_time`, `status`)
SELECT * FROM (
    SELECT '深圳', '101280601', 'HEAVY_RAIN', 'RED', '深圳市暴雨红色预警', '预计3小时内降雨量将达100毫米以上，请做好防洪准备。', DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 6 HOUR), 1
) AS s
WHERE NOT EXISTS (SELECT 1 FROM `weather_warning` w WHERE w.`city_code` = '101280601' AND w.`warning_type` = 'HEAVY_RAIN');

-- 4. 天气收藏（testuser用户ID=2，表上有 uk_user_city 唯一约束）
INSERT IGNORE INTO `weather_favorite` (`user_id`, `city`, `city_code`, `remark`) VALUES
(2, '北京', '101010100', '首都'),
(2, '上海', '101020100', '出差常去'),
(2, '广州', '101280101', '老家');

-- 5. 个性化推送记录
INSERT INTO `push_record` (`user_id`, `push_type`, `push_content`, `push_status`, `push_time`)
SELECT * FROM (
    SELECT 2, 'DAILY_WEATHER', '北京今日天气：晴，最高温32℃，最低温24℃，东南风3级，适合外出活动。', 1, NOW()
) AS s
WHERE NOT EXISTS (
    SELECT 1 FROM `push_record` p
    WHERE p.`user_id` = 2 AND p.`push_type` = 'DAILY_WEATHER' AND p.`push_content` LIKE '北京今日天气：%'
);

INSERT INTO `push_record` (`user_id`, `push_type`, `push_content`, `push_status`, `push_time`)
SELECT * FROM (
    SELECT 2, 'WARNING', '北京市高温黄色预警：今日最高气温将达35℃以上，请注意防暑降温。', 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)
) AS s
WHERE NOT EXISTS (
    SELECT 1 FROM `push_record` p
    WHERE p.`user_id` = 2 AND p.`push_type` = 'WARNING'
);

INSERT INTO `push_record` (`user_id`, `push_type`, `push_content`, `push_status`, `push_time`)
SELECT * FROM (
    SELECT 2, 'DAILY_WEATHER', '上海明日天气：多云，最高温31℃，最低温23℃，适合出行。', 0, NULL
) AS s
WHERE NOT EXISTS (
    SELECT 1 FROM `push_record` p
    WHERE p.`user_id` = 2 AND p.`push_type` = 'DAILY_WEATHER' AND p.`push_content` LIKE '上海明日天气：%'
);

-- 6. 天气统计数据（2025年1-12月，表上有 uk_city_month 唯一约束）
INSERT IGNORE INTO `weather_statistics` (`city`, `city_code`, `stat_year`, `stat_month`, `avg_temp`, `max_temp`, `min_temp`, `avg_humidity`, `total_precip`, `rain_days`, `sunny_days`, `cloudy_days`) VALUES
('北京', '101010100', 2025, 1, -1.5, 5.2, -7.8, 42, 3.2, 2, 18, 11),
('北京', '101010100', 2025, 2, 1.8, 8.5, -4.2, 44, 5.8, 3, 15, 10),
('北京', '101010100', 2025, 3, 8.2, 15.3, 2.1, 45, 12.5, 4, 17, 10),
('北京', '101010100', 2025, 4, 16.5, 23.8, 10.2, 48, 28.5, 6, 16, 8),
('北京', '101010100', 2025, 5, 22.8, 29.5, 16.5, 52, 42.0, 7, 17, 7),
('北京', '101010100', 2025, 6, 27.5, 33.2, 22.0, 58, 85.5, 9, 15, 6),
('北京', '101010100', 2025, 7, 29.8, 35.5, 25.2, 72, 195.0, 12, 13, 6),
('北京', '101010100', 2025, 8, 28.2, 33.8, 23.5, 70, 168.5, 10, 15, 6),
('北京', '101010100', 2025, 9, 23.5, 29.2, 18.0, 62, 58.0, 6, 17, 7),
('北京', '101010100', 2025, 10, 15.8, 22.5, 10.0, 55, 18.5, 4, 18, 9),
('北京', '101010100', 2025, 11, 6.5, 13.2, 0.8, 50, 8.2, 3, 16, 11),
('北京', '101010100', 2025, 12, -0.2, 6.5, -5.8, 45, 2.8, 1, 18, 12);

-- 7. 用户预警通知（按预警记录实时解析 warning_id，避免引用失效的ID）
INSERT INTO `user_warning_notice` (`user_id`, `warning_id`, `is_read`)
SELECT 2, w.`id`, 0 FROM `weather_warning` w
WHERE w.`city_code` = '101010100' AND w.`warning_type` = 'HIGH_TEMP'
  AND NOT EXISTS (SELECT 1 FROM `user_warning_notice` n WHERE n.`user_id` = 2 AND n.`warning_id` = w.`id`)
LIMIT 1;

INSERT INTO `user_warning_notice` (`user_id`, `warning_id`, `is_read`)
SELECT 2, w.`id`, 1 FROM `weather_warning` w
WHERE w.`city_code` = '101020100' AND w.`warning_type` = 'HEAVY_RAIN'
  AND NOT EXISTS (SELECT 1 FROM `user_warning_notice` n WHERE n.`user_id` = 2 AND n.`warning_id` = w.`id`)
LIMIT 1;
