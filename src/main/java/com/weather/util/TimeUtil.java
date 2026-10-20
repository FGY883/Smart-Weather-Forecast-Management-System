package com.weather.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 统一时区入口：天气数据以东八区（Asia/Shanghai）为准，
 * 避免部署主机时区非 CN 时，weather_date 的按日去重键与"当前月"判断跨日错位。
 */
public final class TimeUtil {

    public static final ZoneId CN = ZoneId.of("Asia/Shanghai");

    private TimeUtil() {}

    public static LocalDate today() {
        return LocalDate.now(CN);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(CN);
    }
}
