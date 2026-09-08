package com.weather.controller;

import com.alibaba.fastjson2.JSONObject;
import com.weather.common.Result;
import com.weather.entity.SysUser;
import com.weather.entity.WeatherFavorite;
import com.weather.service.SysUserService;
import com.weather.service.WeatherCardService;
import com.weather.service.WeatherFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Aether 手绘天气展示页控制器。
 *
 * 数据装配逻辑统一在 {@link WeatherCardService}，本控制器只负责视图模型与用户态。
 */
@Controller
public class AetherController {

    /** 默认展示的 6 座城市 */
    private static final List<String> DEFAULT_CITIES =
            Arrays.asList("北京", "上海", "广州", "成都", "哈尔滨", "杭州");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private WeatherCardService weatherCardService;

    @Autowired
    private WeatherFavoriteService weatherFavoriteService;

    @Autowired
    private SysUserService userService;

    /**
     * 展示页：默认 6 张城市卡；已登录用户优先使用其收藏城市（最多 6 个）。
     */
    @GetMapping("/weather/aether")
    public String aether(Model model) {
        SysUser user = getCurrentUser();
        List<String> cities = DEFAULT_CITIES;

        if (user != null) {
            try {
                List<WeatherFavorite> favorites = weatherFavoriteService.getUserFavorites(user.getId());
                if (favorites != null && !favorites.isEmpty()) {
                    List<String> favCities = favorites.stream()
                            .map(WeatherFavorite::getCity)
                            .filter(c -> c != null && !c.trim().isEmpty())
                            .distinct()
                            .limit(6)
                            .collect(Collectors.toList());
                    if (!favCities.isEmpty()) {
                        cities = favCities;
                    }
                }
            } catch (Exception ignored) {
                // 收藏读取失败时回退到默认城市
            }
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        try {
            cards = cities.parallelStream()
                    .map(weatherCardService::loadCity)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
        }

        if (cards.isEmpty()) {
            cards = cities.stream()
                    .map(weatherCardService::loadCity)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        model.addAttribute("cardsJson", JSONObject.toJSONString(cards));
        model.addAttribute("cityLibJson", JSONObject.toJSONString(weatherCardService.getCityList()));
        model.addAttribute("user", user);
        model.addAttribute("updatedAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(TIME_FMT));
        return "aether";
    }

    /**
     * 供页面异步拉取单个城市的真实天气（用于"添加城市"）。
     */
    @GetMapping("/api/aether/weather")
    @ResponseBody
    public Result<Map<String, Object>> aetherWeather(@RequestParam String city) {
        if (city == null || city.trim().isEmpty()) {
            return Result.error("城市名称不能为空");
        }
        Map<String, Object> data = weatherCardService.loadCity(city.trim());
        if (data == null) {
            return Result.error("未能获取「" + city + "」的天气数据");
        }
        return Result.success(data);
    }

    private SysUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return null;
        }
        return userService.findByUsername(auth.getName());
    }
}