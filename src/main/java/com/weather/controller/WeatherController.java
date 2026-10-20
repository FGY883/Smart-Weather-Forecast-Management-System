package com.weather.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weather.entity.*;
import com.weather.service.*;
import com.weather.util.CityNameGuard;
import com.weather.util.TimeUtil;
import com.weather.util.UserContext;
import com.weather.util.WeatherApiUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class WeatherController {

    /** 城市卡默认展示的热门城市，全站统一 */
    private static final List<String> DEFAULT_CITIES =
            Arrays.asList("北京", "上海", "广州", "成都", "杭州", "西安");

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
        model.addAttribute("user", user);
        return "index";
    }

    @GetMapping("/weather/search")
    public String searchWeather(@RequestParam(required = false) String city, Model model) {
        SysUser user = userContext.currentUser();
        if (city == null || city.trim().isEmpty()) {
            return "redirect:/";
        }
        String cityName = city.trim();
        if (CityNameGuard.isProvince(cityName)) {
            model.addAttribute("searchError", CityNameGuard.message());
            model.addAttribute("user", user);
            return "index";
        }
        try {
            JSONObject result = weatherApiUtil.searchCity(cityName);
            String cityCode = firstCityCode(result, cityName);
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
            model.addAttribute("accuracy", aiPredictionService.getAccuracy(cityCode));
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
        if (CityNameGuard.isProvince(cityName)) {
            model.addAttribute("forecastError", CityNameGuard.message());
            model.addAttribute("user", userContext.currentUser());
            return "ai-forecast";
        }
        try {
            JSONObject result = weatherApiUtil.searchCity(cityName);
            String cityCode = firstCityCode(result, cityName);
            if (cityCode != null) {
                String resolved = firstCityName(result, cityName);
                model.addAttribute("forecast", weatherDataService.getForecast7Day(cityCode, resolved));
                model.addAttribute("predictions", loadPredictions(cityCode, resolved));
                model.addAttribute("accuracy", aiPredictionService.getAccuracy(cityCode));
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
                             @RequestParam(required = false) String startMonth,
                             @RequestParam(required = false) String endMonth,
                             Model model) {
        fillStatisticsModel(model, cityCode, cityName, startMonth, endMonth);
        model.addAttribute("user", userContext.currentUser());
        return "statistics";
    }

    @GetMapping("/weather/statistics/search")
    public String statisticsSearch(@RequestParam(required = false) String city,
                                   @RequestParam(required = false) String startMonth,
                                   @RequestParam(required = false) String endMonth,
                                   Model model) {
        model.addAttribute("currentMonth", YearMonth.from(TimeUtil.now()).toString());
        if (city == null || city.trim().isEmpty()) {
            model.addAttribute("statsError", "请输入城市名称");
            model.addAttribute("user", userContext.currentUser());
            return "statistics";
        }
        String cityName = city.trim();
        if (CityNameGuard.isProvince(cityName)) {
            model.addAttribute("statsError", CityNameGuard.message());
            model.addAttribute("user", userContext.currentUser());
            return "statistics";
        }
        try {
            JSONObject result = weatherApiUtil.searchCity(cityName);
            String cityCode = firstCityCode(result, cityName);
            if (cityCode == null) {
                model.addAttribute("statsError", "未找到城市: " + cityName);
                model.addAttribute("user", userContext.currentUser());
                return "statistics";
            }
            fillStatisticsModel(model, cityCode, firstCityName(result, cityName), startMonth, endMonth);
        } catch (Exception e) {
            model.addAttribute("statsError", "统计数据获取失败，请稍后重试");
        }
        model.addAttribute("user", userContext.currentUser());
        return "statistics";
    }

    /**
     * 预警列表页：灾害预警数据源为华风爱科（和风预警为付费接口）。
     * 华风按地点查询、无"全国预警列表"接口，故默认页给热门城市快捷入口。
     */
    @GetMapping("/weather/warning")
    public String warnings(Model model) {
        model.addAttribute("hotCities", DEFAULT_CITIES);
        model.addAttribute("user", userContext.currentUser());
        return "warnings";
    }

    /**
     * 查询某个城市的实时预警：走华风爱科。
     * 城市名 → Location Key → 预警列表，模板消费统一字段。
     */
    @GetMapping("/weather/warning/search")
    public String warningSearch(@RequestParam(required = false) String city, Model model) {
        SysUser user = userContext.currentUser();
        if (city == null || city.trim().isEmpty()) {
            return "redirect:/weather/warning";
        }
        String cityName = city.trim();
        if (CityNameGuard.isProvince(cityName)) {
            model.addAttribute("searchError", CityNameGuard.message());
            model.addAttribute("warnings", new JSONArray());
            model.addAttribute("hotCities", DEFAULT_CITIES);
            model.addAttribute("user", user);
            return "warnings";
        }
        try {
            JSONArray warnings = weatherWarningService.fetchWarningsFromWeathercn(cityName);
            model.addAttribute("warnings", warnings);
            model.addAttribute("searchCity", cityName);
        } catch (Exception e) {
            model.addAttribute("searchError", "预警查询失败，请稍后重试");
            model.addAttribute("warnings", new JSONArray());
        }
        model.addAttribute("hotCities", DEFAULT_CITIES);
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
        model.addAttribute("currentCity", cityName);
        model.addAttribute("currentCityCode", cityCode);
        if (user != null) {
            model.addAttribute("isFavorited", weatherFavoriteService.isFavorited(user.getId(), cityCode));
        }
    }

    /**
     * 统计页数据装配：按"月份区间"查询，并以当前时间为上限截断（未来月份一律不展示）。
     * 区间缺省为「当前年 1 月 ~ 当前月」。缺失的年份会先按月生成（生成端同样跳过未来月份）。
     */
    private void fillStatisticsModel(Model model, String cityCode, String cityName,
                                     String startMonth, String endMonth) {
        YearMonth now = YearMonth.from(TimeUtil.now());
        YearMonth end = parseMonth(endMonth, now);
        if (end.isAfter(now)) end = now;                       // 以当前时间为准
        YearMonth start = parseMonth(startMonth, YearMonth.of(end.getYear(), 1));
        if (start.isAfter(end)) start = end;                   // 顺序纠正
        // 封顶：不得早于「当前往前 3 年」，且最多跨 3 个自然年，避免超大区间触发海量生成/外部调用
        YearMonth floor = now.minusYears(3);
        if (start.isBefore(floor)) start = floor;
        if (start.isBefore(end.minusYears(2))) start = end.minusYears(2);

        List<WeatherStatistics> result = new ArrayList<>();
        int realDays = 0;
        try {
            for (int y = start.getYear(); y <= end.getYear(); y++) {
                List<WeatherStatistics> ys = weatherStatisticsService.getCityStatistics(cityCode, y);
                if (ys == null) ys = new ArrayList<>();
                Set<Integer> present = new HashSet<>();
                for (WeatherStatistics s : ys) {
                    if (s.getStatMonth() != null) present.add(s.getStatMonth());
                }
                // 补齐区间内、且不晚于当前月的缺失月份（未来月份永不生成）
                boolean generated = false;
                for (int m = 1; m <= 12; m++) {
                    YearMonth ym = YearMonth.of(y, m);
                    if (ym.isBefore(start) || ym.isAfter(end) || ym.isAfter(now)) continue;
                    if (!present.contains(m)) {
                        try {
                            weatherStatisticsService.generateMonthlyStatistics(cityCode, cityName, y, m);
                            generated = true;
                        } catch (Exception ignored) {
                            // 单月失败不影响其余月份
                        }
                    }
                }
                if (generated) {
                    ys = weatherStatisticsService.getCityStatistics(cityCode, y);
                    if (ys == null) ys = new ArrayList<>();
                }
                for (WeatherStatistics s : ys) {
                    if (s.getStatYear() == null || s.getStatMonth() == null) continue;
                    YearMonth sm = YearMonth.of(s.getStatYear(), s.getStatMonth());
                    if (!sm.isBefore(start) && !sm.isAfter(end) && !sm.isAfter(now)) {
                        result.add(s);
                    }
                }
                realDays += weatherStatisticsService.countRealDataDays(cityCode, y);
            }
        } catch (Exception e) {
            model.addAttribute("statsError", "统计数据获取失败，请稍后重试");
        }
        model.addAttribute("statistics", result);
        model.addAttribute("estimated", realDays < 28);
        model.addAttribute("realDays", realDays);
        model.addAttribute("startMonth", start.toString());
        model.addAttribute("endMonth", end.toString());
        model.addAttribute("currentMonth", now.toString());
        model.addAttribute("currentCity", cityName);
        model.addAttribute("currentCityCode", cityCode);
    }

    /** 解析 yyyy-MM（兼容 yyyy-MM-dd）；为空或非法时返回 fallback。 */
    private YearMonth parseMonth(String v, YearMonth fallback) {
        if (v == null) return fallback;
        String s = v.trim();
        if (s.length() < 7) return fallback;
        try {
            return YearMonth.parse(s.substring(0, 7));
        } catch (Exception e) {
            return fallback;
        }
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

    /** 从 geo 查询结果里挑最匹配的一条：优先 name 精确等于查询词，其次 adm1/path 含查询词，最后回退首条。 */
    private JSONObject bestLocation(JSONObject result, String query) {
        if (result == null || result.getJSONArray("location") == null
                || result.getJSONArray("location").isEmpty()) {
            return null;
        }
        JSONArray locs = result.getJSONArray("location");
        String q = query == null ? "" : query.trim();
        if (!q.isEmpty()) {
            for (int i = 0; i < locs.size(); i++) {
                JSONObject o = locs.getJSONObject(i);
                if (o != null && q.equals(o.getString("name"))) return o;
            }
            for (int i = 0; i < locs.size(); i++) {
                JSONObject o = locs.getJSONObject(i);
                if (o == null) continue;
                String path = o.getString("path");
                String adm1 = o.getString("adm1");
                if ((path != null && path.contains(q)) || (adm1 != null && adm1.contains(q))) return o;
            }
        }
        return locs.getJSONObject(0);
    }

    private String firstCityCode(JSONObject result, String query) {
        JSONObject best = bestLocation(result, query);
        return best == null ? null : best.getString("id");
    }

    private String firstCityName(JSONObject result, String fallback) {
        JSONObject best = bestLocation(result, fallback);
        if (best == null) {
            return fallback;
        }
        String name = best.getString("name");
        return (name == null || name.isEmpty()) ? fallback : name;
    }
}
