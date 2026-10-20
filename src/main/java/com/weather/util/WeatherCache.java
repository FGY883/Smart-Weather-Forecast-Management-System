package com.weather.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 进程内 TTL 缓存。
 *
 * 背景：本项目每次页面访问都会同步调用和风天气接口（首页 6 座城市卡 =
 * 12 次外部 HTTP），既拖慢首屏又迅速耗尽 API 调用配额。引入缓存后，
 * 同一城市在 TTL 内只回源一次。
 *
 * 之所以不直接引入 Caffeine/Redis，是为了保持"零新增依赖"，
 * 课程设计场景下进程内缓存已足够，也便于离线演示。
 */
@Component
public class WeatherCache {

    private static final Logger log = LoggerFactory.getLogger(WeatherCache.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private static final class Entry {
        final Object value;
        final long expireAt;

        Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** 回源期间的按 key 互斥锁，避免同一 key 并发未命中时多次打外部接口（缓存击穿）。 */
    private final ConcurrentHashMap<String, Object> loadingLocks = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Entry e = store.get(key);
        if (e == null) {
            return null;
        }
        if (e.expired()) {
            store.remove(key, e);
            return null;
        }
        return (T) e.value;
    }

    public void put(String key, Object value, Duration ttl) {
        store.put(key, new Entry(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    /**
     * 取缓存，缺失时调用 loader 回源并写回缓存。
     * loader 返回 null 时不写入缓存，避免把失败结果固化住。
     * 同一 key 的回源串行化：并发未命中时只有一个线程真正调用 loader，其余等待复用结果。
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        Object lock = loadingLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                // 双重检查：拿到锁后再看一次，可能已被前一个线程回填
                T again = get(key, type);
                if (again != null) {
                    return again;
                }
                T loaded;
                try {
                    loaded = loader.get();
                } catch (Exception e) {
                    log.warn("缓存回源失败: key={}, err={}", key, e.getMessage());
                    return null;
                }
                if (loaded != null) {
                    put(key, loaded, ttl == null ? DEFAULT_TTL : ttl);
                }
                return loaded;
            }
        } finally {
            loadingLocks.remove(key, lock);
        }
    }

    public void evict(String key) {
        store.remove(key);
    }

    /** 按前缀清除（例如写库后清掉某城市所有日期区间的 hist: 读缓存）。 */
    public void evictPrefix(String prefix) {
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clear() {
        store.clear();
    }

    /** 每小时清理一次已过期条目，避免长期运行后内存缓慢膨胀 */
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanup() {
        int before = store.size();
        store.values().removeIf(Entry::expired);
        int removed = before - store.size();
        if (removed > 0) {
            log.debug("天气缓存清理过期条目: {}", removed);
        }
    }
}
