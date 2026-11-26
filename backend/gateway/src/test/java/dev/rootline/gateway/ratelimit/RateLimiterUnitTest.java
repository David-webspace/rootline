package dev.rootline.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rootline.gateway.config.GatewayProperties.Policy;
import dev.rootline.gateway.ratelimit.RateLimiter.Decision;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterUnitTest {

    private final Policy policy = new Policy(3, 1.0);

    @Test
    void localBucketAllowsBurstThenRefillsOverTime() {
        AtomicLong now = new AtomicLong(1_000_000);
        LocalTokenBucketRateLimiter limiter = new LocalTokenBucketRateLimiter(now::get);

        assertThat(limiter.tryAcquire("k", policy).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", policy).allowed()).isTrue();
        Decision third = limiter.tryAcquire("k", policy);
        assertThat(third.allowed()).isTrue();
        assertThat(third.remaining()).isZero();

        Decision denied = limiter.tryAcquire("k", policy);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(1);

        now.addAndGet(1_000);
        assertThat(limiter.tryAcquire("k", policy).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", policy).allowed()).isFalse();
    }

    @Test
    void bucketsAreIndependentPerKey() {
        LocalTokenBucketRateLimiter limiter = new LocalTokenBucketRateLimiter(() -> 0L);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a", policy);
        }
        assertThat(limiter.tryAcquire("a", policy).allowed()).isFalse();
        assertThat(limiter.tryAcquire("b", policy).allowed()).isTrue();
    }

    @Test
    void refillNeverExceedsCapacity() {
        AtomicLong now = new AtomicLong(0);
        LocalTokenBucketRateLimiter limiter = new LocalTokenBucketRateLimiter(now::get);
        limiter.tryAcquire("k", policy);
        now.addAndGet(3_600_000);
        assertThat(limiter.tryAcquire("k", policy).remaining()).isEqualTo(2);
    }

    @Test
    void failoverUsesLocalLimiterWhenPrimaryFails() {
        RateLimiter broken = (key, p) -> {
            throw new IllegalStateException("redis down");
        };
        FailoverRateLimiter limiter = new FailoverRateLimiter(broken, new LocalTokenBucketRateLimiter(() -> 0L));

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", policy).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire("k", policy).allowed())
                .as("Redis 故障時仍然要限流，不能全部放行")
                .isFalse();
    }
}
