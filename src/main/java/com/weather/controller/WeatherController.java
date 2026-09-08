package com.weather.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weather.entity.*;
import com.weather.service.*;
import com.weather.util.UserContext;
import com.weather.util.WeatherApiUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class WeatherController {

    /** 城市卡默认展示的热门城市，全站统一 */
    private static final List<String> DEFAULT_CITIES =
            Arrays.asList("北京", "上海", "广州", "成都", "杭州", "西安");

    /** 统计页可选择的年份跨度 */
    private static final int YEAR_RANGE = 5;

    @Autowired
    private WeatherDataService weatherDataService;

    @Autowired
    private AiPredictionService aiPredictionService;

    @Autowired
    private WeatherWarningService weatherWarningService;

    @Autowired
    private WeatherFavoriteService weatherFavoriteService;

    @Autowired
    private WeatherStatisticsService weatherStatisticsService;

    @Autowired
    private WeatherCardService weatherCardService;

    @Autowired
    private WeatherApiUtil weatherApiUtil;

    @Autowired
    private UserContext userContext;

    @GetMapping("/")
    public String index(Model model) {
        SysUser user = userContext.currentUser();
        String city = "北京";
        String cityCode = "101010100";

        if (user != null && user.getCity() != null && !user.getCity().isEmpty()) {
            city = user.getCity();
            cityCode = user.getCityCode();
        }
        try {
            fillWeatherModel(model, cityCode, city, user);
        } catch (Exception e) {
            model.addAttribute("weatherError", "天气数据获取失败，请稍后重试");
        }
        return "index";
    }

    @GetMapping("/weather/search")
    public String searchWeather(@RequestParam(required = false) String city, Model model) {
        SysUser user = userContext.currentUser();
        if (city == null || city.trim().isEmpty()) {
            return "redirect:/";
        }
        String cityName = city.trim();
        try {
            JSONObject result = weatherApiUtil.searchCity(cityName);
            String cityCode = firstCityCode(result);
            if (cityCode != null) {
                String resolvedName = firstCityName(result, cityName);
                fillWeatherModel(model, cityCode, resolvedName, user);
            } else {
                model.addAttribute("searchError", "未找到城市: " + cityName);
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
        SysUser user = userContext.currentUser();
        try {
            fillWeatherModel(model, cityCode, cityName, user);
        } catch (Exception e) {
            model.addAttribute("weatherError", "天气数据获取失败，请稍后重试");
        }
        model.addAttribute("user", user);
        return "detail";
    }

    /**
     * 预报页：官方 7 天预报 + AI 模型预测对比
     */
    @GetMapping("/weather/ai-forecast")
    public String aiForecastDefault(Model model) {
        return aiForecast("101010100", "北京", model);
    }

    @GetMapping("/weather/ai-forecast/{cityCode}")
    public String aiForecast(@PathVariable String cityCode,
                             @RequestParam(defaultValue = "北京") String cityName,
                             Model model) {
        try {
            List<WeatherData> forecast = weatherDataService.getForecast7Day(cityCode, cityName);
            if (forecast == null || forecast.isEmpty()) {
                model.addAttribute("forecastError", "暂无该城市的天气预报数据，请稍后再试");
            }
            model.addAttribute("forecast", forecast);
            model.addAttribute("predictions", loadPredictions(cityCode, cityName));
        } catch (Exception e) {
            model.addAttribute("forecastError", "天气数据获取失败，请稍后重试");
        }
        model.addAttribute("currentCity", cityName);
        model.addAttribute("currentCityCode", cityCode);
        model.addAttribute("user", userContext.currentUser());
        return "ai-forecast";
    }

    @GetMapping("/weather/ai-forecast/search")
    public String aiForecastSearch(@RequestParam(required = false) String city, Model model) {
        if (city == null || city.trim().isEmpty()) {
            return "redirect:/weather/ai-forecast";
        }
        String cityName = city.trim();
        try {
            JSONObject result = weatherApiUtil.searchCity(cityName);
            String cityCode = firstCityCode(result);
            if (cityCode != null) {
                String resolved = firstCityName(result, cityName);
                model.addAttribute("forecast", weatherDataService.getForecast7Day(cityCode, resolved));
                model.addAttribute("predictions", loadPredictions(cityCode, resolved));
                model.addAttribute("currentCity", resolved);
                model.addAttribute("currentCityCode", cityCode);
            } else {
                model.addAttribute("forecastError", "未找到城市: " + cityName);
            }
        } catch (Exception e) {
            model.addAttribute("forecastError", "天气数据获取失败，请稍后重试");
        }
        model.addAttribute("user", userContext.currentUser());
        return "ai-forecast";
    }

    @GetMapping("/weather/statistics/{cityCode}")
    public String statistics(@PathVariable String cityCode,
                             @RequestParam(defaultValue = "北京") String cityName,
                             @RequestParam(required = false) Integer year,
                             Model model) {
        fillStatisticsModel(model, cityCode, cityName, year == null ? LocalDate.now().getYear() : year);
        model.addAttribute("user", userContext.currentUser());
        return "statistics";
    }

    @GetMapping("/weather/statistics/search")
    public String statisticsSearch(@RequestParam(required = false) String city,
                                   @RequestParam(required = false) Integer year,
                                   Model model) {
        if (city == null || city.trim().isEmpty()) {
            model.addAttribute("statsError", "请输入城市名称");
            model.addAttribute("user", userContext.currentUser());
            return "statistics";
        }
        String cityName = city.trim();
        try {
            JSONObject result = weatherApiUtil.searchCity(cityName);
            String cityCode = firstCityCode(result);
            if (cityCode == null) {
                model.addAttribute("statsError", "未找到城市: " + cityName);
                model.addAttribute("user", userContext.currentUser());
                return "statistics";
            }
            fillStatisticsModel(model, cityCode, firstCityName(result, cityName),
                    year == null ? LocalDate.now().getYear() : year);
        } catch (Exception e) {
            model.addAttribute("statsError", "统计数据获取失败，请稍后重试");
        }
        model.addAttribute("user", userContext.currentUser());
        return "statistics";
    }

    /**
     * 预警列表页：直接从和风天气灾害预警 API 拉全国当前预警，
     * 不再走本地阈值合成。空白时显示"暂无预警"提示。
     */
    @GetMapping("/weather/warning")
    public String warnings(Model model) {
        JSONArray cityList = weatherWarningService.fetchAllWarningsFromApi();
        int totalWarnings = sumWarningCounts(cityList);
        model.addAttribute("cityList", cityList != null ? cityList : new JSONArray());
        model.addAttribute("totalWarnings", totalWarnings);
        model.addAttribute("user", userContext.currentUser());
        return "warnings";
    }

    /**
     * 把 JSONArray 里的 warningCount 累加。Ognl 在 Thymeleaf 里调
     * stream() 有兼容性风险，这里提前算好放模型，模板直接 ${totalWarnings}。
     */
    private int sumWarningCounts(JSONArray cityList) {
        if (cityList == null) return 0;
        int total = 0;
        for (int i = 0; i < cityList.size(); i++) {
            Object row = cityList.get(i);
            if (row instanceof JSONObject) {
                Object cnt = ((JSONObject) row).get("warningCount");
                if (cnt instanceof Number) {
                    total += ((Number) cnt).intValue();
                }
            }
        }
        return total;
    }

    /**
     * 查询某个城市的实时预警：直接打 /v7/warning/now，
     * 模板渲染原始结构，避免再次落库造成延迟。
     */
    @GetMapping("/weather/warning/search")
    public String warningSearch(@RequestParam(required = false) String city, Model model) {
        SysUser user = userContext.currentUser();
        if (city == null || city.trim().isEmpty()) {
            return "redirect:/weather/warning";
        }
        String cityName = city.trim();
        try {
            JSONObject result = weatherApiUtil.searchCity(cityName);
            String cityCode = firstCityCode(result);
            if (cityCode != null) {
                String resolved = firstCityName(result, cityName);
                JSONArray warnings = weatherWarningService.fetchWarningsFromApi(cityCode);
                model.addAttribute("warnings", warnings);
                model.addAttribute("searchCity", resolved);
                model.addAttribute("searchCityCode", cityCode);
                model.addAttribute("cityList", weatherWarningService.fetchAllWarningsFromApi());
            } else {
                model.addAttribute("searchError", "未找到城市: " + cityName);
                model.addAttribute("warnings", new JSONArray());
                model.addAttribute("cityList", weatherWarningService.fetchAllWarningsFromApi());
            }
        } catch (Exception e) {
            model.addAttribute("searchError", "查询失败：" + e.getMessage());
            model.addAttribute("warnings", new JSONArray());
        }
        model.addAttribute("user", user);
        return "warnings";
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /**
     * 统一装配"实时天气 + 7 天预报 + 预警 + 城市卡"，供首页/搜索/详情三处复用
     */
    private void fillWeatherModel(Model model, String cityCode, String cityName, SysUser user) {
        WeatherData realtime = weatherDataService.getRealtimeWeather(cityCode, cityName);
        List<WeatherData> forecast = weatherDataService.getForecast7Day(cityCode, cityName);
        List<WeatherWarning> warnings = weatherWarningService.getActiveWarnings(cityCode);

        model.addAttribute("realtime", realtime);
        model.addAttribute("forecast", forecast);
        model.addAttribute("warnings", warnings);
        model.addAttribute("cityCardsJson", JSONObject.toJSONString(buildCityCards(cityName)));
        model.addAttribute("currentCity", cityName);
        model.addAttribute("currentCityCode", cityCode);
        if (user != null) {
            model.addAttribute("isFavorited", weatherFavoriteService.isFavorited(user.getId(), cityCode));
        }
    }

    /**
     * 城市卡：当前城市优先，其余用固定热门城市补齐 6 张
     */
    private List<Map<String, Object>> buildCityCards(String primaryCity) {
        List<String> cities = new ArrayList<>();
        if (primaryCity != null && !primaryCity.trim().isEmpty()) {
            cities.add(primaryCity.trim());
        }
        for (String c : DEFAULT_CITIES) {
            if (!cities.contains(c)) {
                cities.add(c);
            }
        }
        return cities.stream()
                .limit(6)
                .map(weatherCardService::loadCity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 统计页数据装配：先取已有统计，缺失时按全年生成（跳过未来月份）
     */
    private void fillStatisticsModel(Model model, String cityCode, String cityName, int year) {
        try {
            List<WeatherStatistics> stats = weatherStatisticsService.getCityStatistics(cityCode, year);
            if (stats == null || stats.isEmpty()) {
                generateYearStatistics(cityCode, cityName, year);
                stats = weatherStatisticsService.getCityStatistics(cityCode, year);
            }
            int realDays = weatherStatisticsService.countRealDataDays(cityCode, year);
            model.addAttribute("statistics", stats);
            model.addAttribute("estimated", realDays < 28);
            model.addAttribute("realDays", realDays);
        } catch (Exception e) {
            model.addAttribute("statsError", "统计数据获取失败，请稍后重试");
        }
        model.addAttribute("currentCity", cityName);
        model.addAttribute("currentCityCode", cityCode);
        model.addAttribute("year", year);
        model.addAttribute("years", availableYears(year));
    }

    private void generateYearStatistics(String cityCode, String cityName, int year) {
        LocalDate now = LocalDate.now();
        for (int month = 1; month <= 12; month++) {
            // 当年尚未到来的月份不生成，避免凭空造出"未来统计"
            if (year > now.getYear() || (year == now.getYear() && month > now.getMonthValue())) {
                continue;
            }
            try {
                weatherStatisticsService.generateMonthlyStatistics(cityCode, cityName, year, month);
            } catch (Exception ignored) {
                // 单月失败不影响其余月份
            }
        }
    }

    private List<Integer> availableYears(int current) {
        int thisYear = LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int y = thisYear; y >= thisYear - YEAR_RANGE; y--) {
            years.add(y);
        }
        if (!years.contains(current)) {
            years.add(current);
            Collections.sort(years, Collections.reverseOrder());
        }
        return years;
    }

    /**
     * 取 AI 预测：已有当天结果直接用，没有再触发生成
     */
    private List<AiPrediction> loadPredictions(String cityCode, String cityName) {
        try {
            List<AiPrediction> predictions = aiPredictionService.getLatestPrediction(cityCode);
            if (predictions == null || predictions.isEmpty()) {
                predictions = aiPredictionService.generatePrediction(cityCode, cityName);
            }
            return predictions;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String firstCityCode(JSONObject result) {
        if (result == null || result.getJSONArray("location") == null
                || result.getJSONArray("location").isEmpty()) {
            return null;
        }
        return result.getJSONArray("location").getJSONObject(0).getString("id");
    }

    private String firstCityName(JSONObject result, String fallback) {
        if (result == null || result.getJSONArray("location") == null
                || result.getJSONArray("location").isEmpty()) {
            return fallback;
        }
        String name = result.getJSONArray("location").getJSONObject(0).getString("name");
        return (name == null || name.isEmpty()) ? fallback : name;
    }
}
