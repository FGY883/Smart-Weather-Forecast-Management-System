package com.weather.controller;

import com.weather.common.Result;
import com.weather.entity.*;
import com.weather.service.*;
import com.weather.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private WeatherFavoriteService weatherFavoriteService;

    @Autowired
    private AiPredictionService aiPredictionService;

    @Autowired
    private WeatherWarningService weatherWarningService;

    @Autowired
    private PushRecordService pushRecordService;

    @Autowired
    private UserContext userContext;

    @PostMapping("/favorite/add")
    public Result<?> addFavorite(@RequestParam String city, @RequestParam String cityCode) {
        SysUser user = userContext.currentUser();
        if (user == null) return Result.error(401, "请先登录");
        if (city == null || city.trim().isEmpty() || cityCode == null || cityCode.trim().isEmpty()) {
            return Result.error(400, "参数不完整");
        }
        if (weatherFavoriteService.addFavorite(user.getId(), city.trim(), cityCode.trim())) {
            return Result.success("收藏成功");
        }
        return Result.error("已收藏该城市");
    }

    @PostMapping("/favorite/remove")
    public Result<?> removeFavorite(@RequestParam(required = false) Long favoriteId,
                                    @RequestParam(required = false) String cityCode) {
        SysUser user = userContext.currentUser();
        if (user == null) return Result.error(401, "请先登录");
        if (favoriteId != null) {
            return weatherFavoriteService.removeFavorite(user.getId(), favoriteId)
                    ? Result.success("取消收藏成功") : Result.error("取消收藏失败");
        }
        if (cityCode != null && !cityCode.isEmpty()) {
            boolean ok = weatherFavoriteService.removeByCityCode(user.getId(), cityCode);
            return ok ? Result.success("取消收藏成功") : Result.error("未找到对应收藏");
        }
        return Result.error(400, "favoriteId 或 cityCode 必传");
    }

    @GetMapping("/favorite/list")
    public Result<List<WeatherFavorite>> listFavorites() {
        SysUser user = userContext.currentUser();
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(weatherFavoriteService.getUserFavorites(user.getId()));
    }

    @PostMapping("/prediction/generate")
    public Result<List<AiPrediction>> generatePrediction(@RequestParam String cityCode,
                                                         @RequestParam String cityName) {
        try {
            return Result.success(aiPredictionService.generatePrediction(cityCode, cityName));
        } catch (Exception e) {
            return Result.error("AI 预测生成失败: " + e.getMessage());
        }
    }

    @GetMapping("/warning/active")
    public Result<List<WeatherWarning>> getActiveWarnings(@RequestParam String cityCode) {
        return Result.success(weatherWarningService.getActiveWarnings(cityCode));
    }

    @GetMapping("/warning/all")
    public Result<List<WeatherWarning>> getAllWarnings() {
        return Result.success(weatherWarningService.getAllActiveWarnings());
    }

    @GetMapping("/push/records")
    public Result<List<PushRecord>> getPushRecords(@RequestParam(required = false, defaultValue = "20") int limit) {
        SysUser user = userContext.currentUser();
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(pushRecordService.getUserPushRecords(user.getId(), limit));
    }
}
