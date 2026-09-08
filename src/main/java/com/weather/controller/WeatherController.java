package com.weather.controller;

import com.weather.common.Result;
import com.weather.entity.*;
import com.weather.service.*;
import com.weather.util.WeatherApiUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WeatherController {

    @Autowired
    private WeatherDataService weatherDataService;

    @Autowired
    private AiPredictionService aiPredictionService;

    @Autowired
    private WeatherWarningService weatherWarningService;

    @Autowired
    private WeatherFavoriteService weatherFavoriteService;

    @Autowired
    private SysUserService userService;

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private WeatherStatisticsService weatherStatisticsService;

    @Autowired
    private WeatherCardService weatherCardService;

    @GetMapping("/")
    public String index(Model model) {
        SysUser user = getCurrentUser();
        String city = "北京";
        String cityCode = "101010100";

        if (user != null && user.getCity() != null && !user.getCity().isEmpty()) {
            city = user.getCity();
            cityCode = user.getCityCode();
        }

        try {
            WeatherData realtime = weatherDataService.getRealtimeWeather(cityCode, city);
            List<WeatherData> forecast = weatherDataService.getForecast7Day(cityCode, city);
            List<WeatherWarning> warnings = weatherWarningService.getActiveWarnings(cityCode);

            // Aether 城市卡：当前城市 + 5 个热门默认城市
            java.util.List<String> cityList = new java.util.ArrayList<>();
            cityList.add(city);
            for (String c : java.util.Arrays.asList("上海", "广州", "成都", "杭州", "西安")) {
                if (!c.equals(city)) cityList.add(c);
            }
            List<Map<String, Object>> cityCards = cityList.stream()
                    .map(weatherCardService::loadCity)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

            model.addAttribute("realtime", realtime);
            model.addAttribute("forecast", forecast);
            model.addAttribute("warnings", warnings);
            model.addAttribute("cityCardsJson", com.alibaba.fastjson2.JSONObject.toJSONString(cityCards));

            if (user != null) {
                model.addAttribute("isFavorited", weatherFavoriteService.isFavorited(user.getId(), cityCode));
            }
        } catch (Exception e) {
            model.addAttribute("weatherError", "天气数据获取失败，请稍后重试");
        }

        model.addAttribute("currentCity", city);
        model.addAttribute("currentCityCode", cityCode);
        model.addAttribute("user", user);
        return "index";
    }

    @GetMapping("/weather/search")
    public String searchWeather(@RequestParam String city, Model model) {
        SysUser user = getCurrentUser();
        try {
            com.alibaba.fastjson2.JSONObject result = weatherApiUtil.searchCity(city);
            if (result != null && result.getJSONArray("location") != null && !result.getJSONArray("location").isEmpty()) {
                com.alibaba.fastjson2.JSONObject loc = result.getJSONArray("location").getJSONObject(0);
                String cityCode = loc.getString("id");
                String cityName = loc.getString("name");

                WeatherData realtime = weatherDataService.getRealtimeWeather(cityCode, cityName);
                List<WeatherData> forecast = weatherDataService.getForecast7Day(cityCode, cityName);
                List<WeatherWarning> warnings = weatherWarningService.getActiveWarnings(cityCode);

                // Aether 城市卡：搜索城市 + 5 个热门默认城市
                java.util.List<String> cityList = new java.util.ArrayList<>();
                cityList.add(cityName);
                for (String c : java.util.Arrays.asList("北京", "上海", "广州", "成都", "杭州")) {
                    if (!c.equals(cityName)) cityList.add(c);
                }
                List<Map<String, Object>> cityCards = cityList.stream()
                        .map(weatherCardService::loadCity)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList());

                model.addAttribute("realtime", realtime);
                model.addAttribute("forecast", forecast);
                model.addAttribute("warnings", warnings);
                model.addAttribute("cityCardsJson", com.alibaba.fastjson2.JSONObject.toJSONString(cityCards));
                model.addAttribute("currentCity", cityName);
                model.addAttribute("currentCityCode", cityCode);

                if (user != null) {
                    model.addAttribute("isFavorited", weatherFavoriteService.isFavorited(user.getId(), cityCode));
                }
            } else {
                model.addAttribute("searchError", "未找到城市: " + city);
            }
        } catch (Exception e) {
            model.addAttribute("searchError", "搜索失败，请稍后重试");
        }
        model.addAttribute("user", user);
        return "index";
    }

    @GetMapping("/weather/detail/{cityCode}")
    public String weatherDetail(@PathVariable String cityCode,
                                @RequestParam(defaultValue = "北京") String cityName,
                                Model model) {
        try {
            WeatherData realtime = weatherDataService.getRealtimeWeather(cityCode, cityName);
            List<WeatherData> forecast = weatherDataService.getForecast7Day(cityCode, cityName);
            List<WeatherWarning> warnings = weatherWarningService.getActiveWarnings(cityCode);

            // Aether 城市卡：当前城市 + 5 个热门默认城市
            java.util.List<String> cityList = new java.util.ArrayList<>();
            cityList.add(cityName);
            for (String c : java.util.Arrays.asList("北京", "上海", "广州", "成都", "杭州")) {
                if (!c.equals(cityName)) cityList.add(c);
            }
            List<Map<String, Object>> cityCards = cityList.stream()
                    .map(weatherCardService::loadCity)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toList());

            model.addAttribute("realtime", realtime);
            model.addAttribute("forecast", forecast);
            model.addAttribute("warnings", warnings);
            model.addAttribute("cityCardsJson", com.alibaba.fastjson2.JSONObject.toJSONString(cityCards));
            model.addAttribute("currentCity", cityName);
            model.addAttribute("currentCityCode", cityCode);

            SysUser user = getCurrentUser();
            if (user != null) {
                model.addAttribute("isFavorited", weatherFavoriteService.isFavorited(user.getId(), cityCode));
            }
        } catch (Exception e) {
            model.addAttribute("weatherError", "天气数据获取失败");
        }
        model.addAttribute("user", getCurrentUser());
        return "detail";
    }

    @GetMapping("/weather/ai-forecast")
    public String aiForecastDefault(Model model) {
        // 默认显示北京的AI预测
        return aiForecast("101010100", "北京", model);
    }

    @GetMapping("/weather/ai-forecast/{cityCode}")
    public String aiForecast(@PathVariable String cityCode,
                             @RequestParam(defaultValue = "北京") String cityName,
                             Model model) {
        try {
            // 直接获取和风天气 7 天预报数据（数据来源：QWeather /v7/weather/7d）
            List<WeatherData> forecast = weatherDataService.getForecast7Day(cityCode, cityName);

            if (forecast == null || forecast.isEmpty()) {
                model.addAttribute("forecastError", "暂无该城市的天气预报数据，请稍后再试");
            }

            model.addAttribute("forecast", forecast);
            model.addAttribute("currentCity", cityName);
            model.addAttribute("currentCityCode", cityCode);
        } catch (Exception e) {
            model.addAttribute("forecastError", "天气数据获取失败: " + e.getMessage());
        }
        model.addAttribute("user", getCurrentUser());
        return "ai-forecast";
    }

    @GetMapping("/weather/ai-forecast/search")
    public String aiForecastSearch(@RequestParam String city, Model model) {
        try {
            // 搜索城市
            com.alibaba.fastjson2.JSONObject result = weatherApiUtil.searchCity(city);
            if (result != null && result.getJSONArray("location") != null && !result.getJSONArray("location").isEmpty()) {
                com.alibaba.fastjson2.JSONObject loc = result.getJSONArray("location").getJSONObject(0);
                String cityCode = loc.getString("id");
                String cityName = loc.getString("name");

                // 直接获取和风天气 7 天预报数据
                List<WeatherData> forecast = weatherDataService.getForecast7Day(cityCode, cityName);

                model.addAttribute("forecast", forecast);
                model.addAttribute("currentCity", cityName);
                model.addAttribute("currentCityCode", cityCode);
            } else {
                model.addAttribute("forecastError", "未找到城市: " + city);
            }
        } catch (Exception e) {
            model.addAttribute("forecastError", "天气数据获取失败: " + e.getMessage());
        }
        model.addAttribute("user", getCurrentUser());
        return "ai-forecast";
    }

    @GetMapping("/weather/statistics/{cityCode}")
    public String statistics(@PathVariable String cityCode,
                             @RequestParam(defaultValue = "北京") String cityName,
                             @RequestParam(required = false) Integer year,
                             Model model) {
        if (year == null) year = java.time.LocalDate.now().getYear();
        try {
            // 先检查是否有统计数据
            List<WeatherStatistics> stats = weatherStatisticsService.getCityStatistics(cityCode, year);

            // 如果没有数据，尝试获取真实天气数据生成统计
            if (stats == null || stats.isEmpty()) {
                // 先获取7天预报数据
                try {
                    List<com.weather.entity.WeatherData> forecastData = weatherApiUtil.getForecast7Day(cityCode, cityName);
                    if (forecastData != null && !forecastData.isEmpty()) {
                        weatherDataService.saveBatch(forecastData);
                    }
                } catch (Exception ignored) {}

                // 生成当前月份的统计数据
                java.time.LocalDate now = java.time.LocalDate.now();
                if (year == now.getYear()) {
                    weatherStatisticsService.generateMonthlyStatistics(cityCode, cityName, year, now.getMonthValue());
                }

                // 生成其他月份的统计数据
                for (int month = 1; month <= 12; month++) {
                    if (year == now.getYear() && month == now.getMonthValue()) continue;
                    try {
                        weatherStatisticsService.generateMonthlyStatistics(cityCode, cityName, year, month);
                    } catch (Exception ignored) {}
                }

                stats = weatherStatisticsService.getCityStatistics(cityCode, year);
            }

            model.addAttribute("statistics", stats);
            model.addAttribute("currentCity", cityName);
            model.addAttribute("currentCityCode", cityCode);
            model.addAttribute("year", year);
        } catch (Exception e) {
            model.addAttribute("statsError", "统计数据获取失败");
        }
        model.addAttribute("user", getCurrentUser());
        return "statistics";
    }

    @GetMapping("/weather/warning")
    public String warnings(Model model) {
        try {
            List<WeatherWarning> warnings = weatherWarningService.getAllActiveWarnings();
            model.addAttribute("warnings", warnings);
        } catch (Exception e) {
            model.addAttribute("warnings", java.util.Collections.emptyList());
        }
        model.addAttribute("user", getCurrentUser());
        return "warnings";
    }

    @GetMapping("/weather/warning/search")
    public String warningSearch(@RequestParam String city, Model model) {
        try {
            // 搜索城市
            com.alibaba.fastjson2.JSONObject result = weatherApiUtil.searchCity(city);
            if (result != null && result.getJSONArray("location") != null && !result.getJSONArray("location").isEmpty()) {
                com.alibaba.fastjson2.JSONObject loc = result.getJSONArray("location").getJSONObject(0);
                String cityCode = loc.getString("id");
                String cityName = loc.getString("name");

                // 先检查并生成预警
                weatherWarningService.checkAndGenerateWarnings(cityCode, cityName);
                // 获取该城市的活跃预警
                List<WeatherWarning> warnings = weatherWarningService.getActiveWarnings(cityCode);
                model.addAttribute("warnings", warnings);
                model.addAttribute("searchCity", cityName);
                model.addAttribute("searchCityCode", cityCode);
            } else {
                model.addAttribute("searchError", "未找到城市: " + city);
                model.addAttribute("warnings", java.util.Collections.emptyList());
            }
        } catch (Exception e) {
            model.addAttribute("searchError", "搜索失败，请稍后重试");
            model.addAttribute("warnings", java.util.Collections.emptyList());
        }
        model.addAttribute("user", getCurrentUser());
        return "warnings";
    }

    @GetMapping("/weather/statistics/search")
    public String statisticsSearch(@RequestParam String city,
                                   @RequestParam(required = false) Integer year,
                                   Model model) {
        if (year == null) year = java.time.LocalDate.now().getYear();
        try {
            // 搜索城市
            com.alibaba.fastjson2.JSONObject result = weatherApiUtil.searchCity(city);
            if (result != null && result.getJSONArray("location") != null && !result.getJSONArray("location").isEmpty()) {
                com.alibaba.fastjson2.JSONObject loc = result.getJSONArray("location").getJSONObject(0);
                String cityCode = loc.getString("id");
                String cityName = loc.getString("name");

                // 如果没有统计数据，尝试生成
                List<WeatherStatistics> stats = weatherStatisticsService.getCityStatistics(cityCode, year);
                if (stats == null || stats.isEmpty()) {
                    // 尝试生成统计数据
                    for (int month = 1; month <= 12; month++) {
                        try {
                            weatherStatisticsService.generateMonthlyStatistics(cityCode, cityName, year, month);
                        } catch (Exception ignored) {}
                    }
                    stats = weatherStatisticsService.getCityStatistics(cityCode, year);
                }

                model.addAttribute("statistics", stats);
                model.addAttribute("currentCity", cityName);
                model.addAttribute("currentCityCode", cityCode);
                model.addAttribute("year", year);
            } else {
                model.addAttribute("statsError", "未找到城市: " + city);
            }
        } catch (Exception e) {
            model.addAttribute("statsError", "统计数据获取失败: " + e.getMessage());
        }
        model.addAttribute("user", getCurrentUser());
        return "statistics";
    }

    private SysUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userService.findByUsername(auth.getName());
    }
}
