package dev.rootline.gateway.ratelimit;

import dev.rootline.gateway.config.GatewayProperties.Policy;

public interface RateLimiter {

    Decision tryAcquire(String key, Policy policy);

    /**
     * @param remaining         取完這次之後剩下的 token（無條件捨去）
     * @param retryAfterSeconds 被拒絕時，最快多久後會有 1 個 token
     */
    record Decision(boolean allowed, long remaining, long retryAfterSeconds) {

        static Decision of(boolean allowed, double tokensLeft, Policy policy) {
            long retryAfter = allowed ? 0 : (long) Math.ceil((1 - tokensLeft) / policy.refillPerSecond());
            return new Decision(allowed, (long) Math.floor(tokensLeft), Math.max(retryAfter, allowed ? 0 : 1));
        }
    }
}
