package com.qiao.common.ratelimit;

import com.qiao.common.error.ApiException;
import com.qiao.common.error.ErrorCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter (single instance deployment; move to Redis when scaling out).
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        Window w = windows.computeIfAbsent(key, k -> new Window(window.toMillis()));
        synchronized (w) {
            w.evictBefore(now);
            if (w.hits.size() >= limit) {
                return false;
            }
            w.hits.addLast(now);
            return true;
        }
    }

    /**
     * @throws ApiException {@code RATE_LIMITED} when the limit is exceeded
     */
    public void check(String key, int limit, Duration window) {
        if (!tryAcquire(key, limit, window)) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
    }

    @Scheduled(fixedDelay = 600_000)
    void evictIdleWindows() {
        long now = clock.millis();
        windows.keySet().forEach(key -> windows.computeIfPresent(key, (k, w) -> {
            synchronized (w) {
                w.evictBefore(now);
                return w.hits.isEmpty() ? null : w;
            }
        }));
    }

    private static final class Window {
        private final long lengthMillis;
        private final Deque<Long> hits = new ArrayDeque<>();

        private Window(long lengthMillis) {
            this.lengthMillis = lengthMillis;
        }

        private void evictBefore(long now) {
            while (!hits.isEmpty() && hits.peekFirst() <= now - lengthMillis) {
                hits.pollFirst();
            }
        }
    }
}
