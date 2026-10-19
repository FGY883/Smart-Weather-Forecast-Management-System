package com.weather.controller;

import com.alibaba.fastjson2.JSONObject;
import com.weather.common.Result;
import com.weather.entity.SysUser;
import com.weather.entity.WeatherFavorite;
import com.weather.service.WeatherCardService;
import com.weather.service.WeatherFavoriteService;
import com.weather.util.CityNameGuard;
import com.weather.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aether 手绘天气展示页控制器。
 *
 * 数据装配逻辑统一在 {@link WeatherCardService}，本控制器只负责视图模型与用户态。
 */
@Controller
public class AetherController {

    /** 默认展示的 6 座城市（与首页城市卡一致） */
    private static final List<String> DEFAULT_CITIES =
            Arrays.asList("北京", "上海", "广州", "成都", "哈尔滨", "杭州");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private WeatherCardService weatherCardService;

    @Autowired
    private WeatherFavoriteService weatherFavoriteService;

    @Autowired
    private UserContext userContext;

    @GetMapping("/weather/aether")
    public String aether(Model model) {
        SysUser user = userContext.currentUser();
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
                // 读取失败时回退到默认城市
            }
        }

        List<Map<String, Object>> cards = cities.stream()
                .map(weatherCardService::loadCity)
                .filter(c -> c != null)
                .collect(Collectors.toList());

        // 经 th:utext 原样注入 <script type="application/json">，须把 "</" 转义为 "<\/"
        // （JSON 合法转义，JSON.parse 可无损还原），防止城市名含 </script> 破坏脚本上下文
        model.addAttribute("cardsJson", JSONObject.toJSONString(cards).replace("</", "<\\/"));
        model.addAttribute("cityLibJson", JSONObject.toJSONString(weatherCardService.getCityList()));
        model.addAttribute("user", user);
        model.addAttribute("updatedAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(TIME_FMT));
        return "aether";
    }

    @GetMapping("/api/aether/weather")
    @ResponseBody
    public Result<Map<String, Object>> aetherWeather(@RequestParam String city) {
        if (city == null || city.trim().isEmpty()) {
            return Result.error("城市名称不能为空");
        }
        if (CityNameGuard.isProvince(city)) {
            return Result.error(CityNameGuard.message());
        }
        Map<String, Object> data = weatherCardService.loadCity(city.trim());
        if (data == null) {
            return Result.error("未能获取「" + city + "」的天气数据");
        }
        return Result.success(data);
    }

    /** 内置城市名列表，供首页/详情页“添加城市”搜索框联想 */
    @GetMapping("/api/aether/cities")
    @ResponseBody
    public Result<List<String>> cities() {
        return Result.success(weatherCardService.getCityList());
    }
}
