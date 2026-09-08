package com.weather.util;

import com.weather.entity.SysUser;
import com.weather.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 当前登录用户上下文。
 *
 * 原先 getRealtimeWeather 式的 getCurrentUser() 在四个 Controller 里各写了一遍，
 * 统一到这里，避免重复。
 */
@Component
public class UserContext {

    @Autowired
    private SysUserService userService;

    /**
     * @return 已登录返回用户实体；未登录或匿名用户返回 null
     */
    public SysUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal == null || "anonymousUser".equals(String.valueOf(principal))) {
            return null;
        }
        try {
            return userService.findByUsername(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }
}
