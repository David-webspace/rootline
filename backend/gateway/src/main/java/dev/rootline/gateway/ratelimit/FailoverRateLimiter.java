package dev.rootline.gateway.ratelimit;

import dev.rootline.gateway.config.GatewayProperties.Policy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis 優先，失敗時改用本機限流。
 * 刻意不採用「Redis 掛了就全部放行」：那等於攻擊者只要打掛 Redis 就能繞過限流。
 * 警告 log 每分鐘最多一則，避免 Redis 斷線時洗版。
 */
public class FailoverRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FailoverRateLimiter.class);
    private static final long WARN_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();

    private final RateLimiter primary;
    private final RateLimiter fallback;
    private final AtomicLong lastWarn = new AtomicLong();

    public FailoverRateLimiter(RateLimiter primary, RateLimiter fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Decision tryAcquire(String key, Policy policy) {
        try {
            return primary.tryAcquire(key, policy);
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            long previous = lastWarn.get();
            if (now - previous > WARN_INTERVAL_MILLIS && lastWarn.compareAndSet(previous, now)) {
                log.warn("Distributed rate limiter unavailable, using per-instance limits: {}", e.toString());
            }
            return fallback.tryAcquire(key, policy);
        }
    }
}
