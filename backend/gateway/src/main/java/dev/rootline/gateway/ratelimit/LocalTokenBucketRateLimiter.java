package dev.rootline.gateway.ratelimit;

import dev.rootline.gateway.config.GatewayProperties.Policy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 單機版令牌桶，只在 Redis 不可用時接手。每台 Gateway 各算各的，
 * 所以叢集總量會變成「單機上限 × 台數」，但至少不會完全不限流。
 */
public class LocalTokenBucketRateLimiter implements RateLimiter {

    private static final int MAX_BUCKETS = 50_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clockMillis;

    public LocalTokenBucketRateLimiter() {
        this(System::currentTimeMillis);
    }

    LocalTokenBucketRateLimiter(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    @Override
    public Decision tryAcquire(String key, Policy policy) {
        long now = clockMillis.getAsLong();
        if (buckets.size() > MAX_BUCKETS) {
            evictIdle(now, policy);
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(policy.capacity(), now));
        return bucket.take(now, policy);
    }

    /** 閒置到已經自然補滿的桶，刪掉和留著效果一樣。 */
    private void evictIdle(long now, Policy policy) {
        long fullAfterMillis = (long) Math.ceil(policy.capacity() / policy.refillPerSecond() * 1000);
        buckets.entrySet().removeIf(e -> now - e.getValue().lastRefill > fullAfterMillis);
    }

    private static final class Bucket {

        private double tokens;
        private long lastRefill;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        synchronized Decision take(long now, Policy policy) {
            tokens = Math.min(policy.capacity(), tokens + Math.max(0, now - lastRefill) / 1000.0 * policy.refillPerSecond());
            lastRefill = now;
            boolean allowed = tokens >= 1;
            if (allowed) {
                tokens -= 1;
            }
            return Decision.of(allowed, tokens, policy);
        }
    }
}
