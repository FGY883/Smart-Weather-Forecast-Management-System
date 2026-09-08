package com.weather.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流。
 *
 * 5 分钟内连续失败 5 次即临时锁定该用户名 10 分钟，
 * 防止演示现场或上线后被暴力破解。
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

    /**
     * 记录一次失败。返回 true 表示该用户已被锁定（超阈值）。
     */
    public boolean fail(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        Counter c = counters.compute(username, (k, old) -> {
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
        Counter c = counters.get(username);
        return c != null && c.lockedUntil > Instant.now().getEpochSecond();
    }

    public void reset(String username) {
        if (username != null) {
            counters.remove(username);
        }
    }
}
