package com.weather.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weather.entity.*;
import com.weather.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class WeatherScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(WeatherScheduledTask.class);

    @Autowired
    private SysUserService userService;

    @Autowired
    private WeatherDataService weatherDataService;

    @Autowired
    private WeatherWarningService weatherWarningService;

    @Autowired
    private WeatherFavoriteService weatherFavoriteService;

    @Autowired
    private AiPredictionService aiPredictionService;

    @Autowired
    private PushRecordService pushRecordService;

    /**
     * 每小时采集一次天气数据。
     * 同一用户可能收藏多个城市，对 city_code 去重后逐个采集，避免无谓的重复 API 调用。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void collectWeatherData() {
        log.info("开始定时采集天气数据...");
        Set<String> cityCodes = new HashSet<>();
        List<WeatherFavorite> allFavorites = weatherFavoriteService.list();
        for (WeatherFavorite fav : allFavorites) {
            if (cityCodes.add(fav.getCityCode())) {
                try {
                    weatherDataService.getRealtimeWeather(fav.getCityCode(), fav.getCity());
                    weatherDataService.getForecast7Day(fav.getCityCode(), fav.getCity());
                    log.info("采集城市天气数据: {}", fav.getCity());
                } catch (Exception e) {
                    log.error("采集天气数据失败 - {}: {}", fav.getCity(), e.getMessage());
                }
            }
        }
        log.info("天气数据采集完成，共处理 {} 个城市", cityCodes.size());
    }

    /**
     * 每天早上 7 点生成 AI 预测并推送。
     * 推送内容是"已存到数据库的模拟推送记录"，作为系统功能演示。
     */
    @Scheduled(cron = "0 0 7 * * ?")
    public void dailyPredictionAndPush() {
        log.info("开始每日AI预测与推送...");
        List<SysUser> users = userService.list(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getPushEnabled, true)
            .eq(SysUser::getStatus, 1));

        for (SysUser user : users) {
            String city = user.getCity() != null && !user.getCity().isEmpty() ? user.getCity() : "北京";
            String cityCode = user.getCityCode() != null && !user.getCityCode().isEmpty() ? user.getCityCode() : "101010100";
            try {
                List<AiPrediction> predictions = aiPredictionService.generatePrediction(cityCode, city);
                List<WeatherWarning> warnings = weatherWarningService.checkAndGenerateWarnings(cityCode, city);

                StringBuilder content = new StringBuilder();
                content.append("【每日天气预报】\n城市: ").append(city).append("\n");
                if (predictions != null && !predictions.isEmpty()) {
                    AiPrediction today = predictions.get(0);
                    content.append("明日天气: ").append(today.getWeatherPred()).append("\n");
                    content.append("温度: ").append(today.getTempMinPred()).append("~").append(today.getTempMaxPred()).append("℃\n");
                    content.append("湿度: ").append(today.getHumidityPred()).append("%\n");
                    content.append("预测置信度: ").append(today.getConfidence()).append("\n");
                }
                if (warnings != null && !warnings.isEmpty()) {
                    content.append("\n预警提醒:\n");
                    for (WeatherWarning w : warnings) {
                        content.append("- ").append(w.getWarningTitle()).append(": ").append(w.getWarningContent()).append("\n");
                    }
                }
                pushRecordService.pushDailyWeather(user.getId(), content.toString());
                log.info("推送天气预报给用户: {}", user.getUsername());
            } catch (Exception e) {
                log.error("推送失败 - 用户{}: {}", user.getUsername(), e.getMessage());
            }
        }
        log.info("每日AI预测与推送完成，共处理 {} 个用户", users.size());
    }

    /**
     * 每 6 小时检查极端天气预警，并清理过期的旧预警
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void checkExtremeWeather() {
        log.info("开始检查极端天气...");
        weatherWarningService.expireOutdatedWarnings();
        Set<String> cityCodes = new HashSet<>();
        List<WeatherFavorite> allFavorites = weatherFavoriteService.list();
        for (WeatherFavorite fav : allFavorites) {
            if (cityCodes.add(fav.getCityCode())) {
                try {
                    List<WeatherWarning> warnings = weatherWarningService.checkAndGenerateWarnings(fav.getCityCode(), fav.getCity());
                    if (!warnings.isEmpty()) {
                        log.info("发现活跃预警 - 城市: {}, 数量: {}", fav.getCity(), warnings.size());
                    }
                } catch (Exception e) {
                    log.error("检查极端天气失败 - {}: {}", fav.getCity(), e.getMessage());
                }
            }
        }
        log.info("极端天气检查完成，共处理 {} 个城市", cityCodes.size());
    }
}
