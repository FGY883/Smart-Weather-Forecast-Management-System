package com.weather.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.weather.common.Result;
import com.weather.entity.SysUser;
import com.weather.service.AssistantService;
import com.weather.util.RateLimiter;
import com.weather.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统使用助手接口。GET 方式，天然免 CSRF；位于 /api 下由 Spring Security 要求登录。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    @Autowired
    private AssistantService assistantService;

    @Autowired
    private UserContext userContext;

    @Autowired
    private RateLimiter rateLimiter;

    @GetMapping("/meta")
    public Result<Map<String, Object>> meta() {
        SysUser user = userContext.currentUser();
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(assistantService.meta());
    }

    @GetMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestParam(required = false) String message,
                                            @RequestParam(required = false) String history) {
        SysUser user = userContext.currentUser();
        if (user == null) return Result.error(401, "请先登录");
        if (!rateLimiter.tryAcquire("assistant:" + user.getId(), 30, 60_000L)) {
            return Result.error(429, "提问过于频繁，请稍后再试");
        }
        return Result.success(assistantService.answer(message, parseHistory(history)));
    }

    /** history 形如 [{"role":"user","content":"..."},...]；解析失败返回空列表。 */
    private List<Map<String, String>> parseHistory(String history) {
        List<Map<String, String>> list = new ArrayList<>();
        if (history == null || history.trim().isEmpty()) return list;
        try {
            JSONArray arr = JSON.parseArray(history);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Map<String, String> m = new LinkedHashMap<>();
                m.put("role", o.getString("role"));
                m.put("content", o.getString("content"));
                list.add(m);
            }
        } catch (Exception ignored) {
            // 容忍非法 history，仅按当前问题作答
        }
        return list;
    }
}

