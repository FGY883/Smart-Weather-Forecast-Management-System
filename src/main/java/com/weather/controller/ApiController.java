package com.weather.controller;

import com.weather.common.Result;
import com.weather.entity.*;
import com.weather.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    private SysUserService userService;

    @PostMapping("/favorite/add")
    public Result<?> addFavorite(@RequestParam String city, @RequestParam String cityCode) {
        SysUser user = getCurrentUser();
        if (user == null) return Result.error(401, "请先登录");
        if (weatherFavoriteService.addFavorite(user.getId(), city, cityCode)) {
            return Result.success("收藏成功");
        }
        return Result.error("已收藏该城市");
    }

    @PostMapping("/favorite/remove")
    public Result<?> removeFavorite(@RequestParam Long favoriteId) {
        SysUser user = getCurrentUser();
        if (user == null) return Result.error(401, "请先登录");
        if (weatherFavoriteService.removeFavorite(user.getId(), favoriteId)) {
            return Result.success("取消收藏成功");
        }
        return Result.error("取消收藏失败");
    }

    @GetMapping("/favorite/list")
    public Result<List<WeatherFavorite>> listFavorites() {
        SysUser user = getCurrentUser();
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(weatherFavoriteService.getUserFavorites(user.getId()));
    }

    @PostMapping("/prediction/generate")
    public Result<List<AiPrediction>> generatePrediction(@RequestParam String cityCode, @RequestParam String cityName) {
        try {
            List<AiPrediction> predictions = aiPredictionService.generatePrediction(cityCode, cityName);
            return Result.success(predictions);
        } catch (Exception e) {
            return Result.error("AI预测生成失败: " + e.getMessage());
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
    public Result<List<PushRecord>> getPushRecords() {
        SysUser user = getCurrentUser();
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(pushRecordService.getUserPushRecords(user.getId()));
    }

    private SysUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userService.findByUsername(auth.getName());
    }
}
