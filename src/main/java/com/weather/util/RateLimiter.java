package com.weather.util;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量滑动窗口限流器（进程内）。仅用于抑制对昂贵外部接口的重复触发，
 * 多实例部署时应替换为 Redis / 网关级限流。
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** key 在 windowMillis 毫秒内最多允许 maxRequests 次；放行则记录本次时间戳。 */
    public boolean tryAcquire(String key, int maxRequests, long windowMillis) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() > windowMillis) {
                hits.pollFirst();
            }
            if (hits.size() >= maxRequests) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }
}
