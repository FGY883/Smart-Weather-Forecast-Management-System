package com.weather.security;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流。
 *
 * 5 分钟内连续失败 5 次即临时锁定 10 分钟，防止暴力破解。
 * 计数键为「用户名 + 客户端 IP」，这样攻击者无法仅凭用户名把某个真实用户锁在门外
 * （锁定只影响同一来源 IP 的尝试），同时仍能挡住单点爆破。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 300;   // 5 分钟窗口
    private static final long LOCK_SECONDS = 600;     // 锁定 10 分钟

    private static final class Counter {
        int attempts;
        long firstAt;       // 第一次失败时间戳
        long lockedUntil;   // 锁定截止时间
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    /** 组合键：用户名 + 当前请求 IP（拿不到请求上下文时退化为仅用户名）。 */
    private String key(String username) {
        String ip = currentIp();
        return ip == null ? username : username + "@" + ip;
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 记录一次失败。返回 true 表示该来源已被锁定（超阈值）。
     */
    public boolean fail(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        String key = key(username);
        Counter c = counters.compute(key, (k, old) -> {
            if (old == null || now - old.firstAt > WINDOW_SECONDS) {
                Counter fresh = new Counter();
                fresh.firstAt = now;
                fresh.attempts = 1;
                return fresh;
            }
            old.attempts += 1;
            return old;
        });
        if (c.attempts >= MAX_ATTEMPTS) {
            c.lockedUntil = now + LOCK_SECONDS;
            return true;
        }
        return false;
    }

    public boolean isLocked(String username) {
        if (username == null) return false;
        Counter c = counters.get(key(username));
        return c != null && c.lockedUntil > Instant.now().getEpochSecond();
    }

    public void reset(String username) {
        if (username != null) {
            counters.remove(key(username));
        }
    }
}
