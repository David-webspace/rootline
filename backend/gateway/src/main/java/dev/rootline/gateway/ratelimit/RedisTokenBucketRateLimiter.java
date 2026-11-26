package dev.rootline.gateway.ratelimit;

import dev.rootline.gateway.config.GatewayProperties.Policy;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 分散式令牌桶。整段邏輯在一支 Lua script 裡原子執行，多台 Gateway 共用同一個桶。
 * <p>
 * 時間取 Redis 的 {@code TIME} 而不是 Gateway 本機時鐘，避免多台機器時鐘不一致造成多補或少補 token。
 * 桶子閒置到「從空補滿所需時間」後自動過期，不會在 Redis 留下冷資料。
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    static final String KEY_PREFIX = "rootline:ratelimit:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TOKEN_BUCKET = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local ts = tonumber(state[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end

            tokens = math.min(capacity, tokens + math.max(0, now - ts) / 1000 * rate)
            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end

            redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('PEXPIRE', key, math.ceil(capacity / rate * 1000) + 1000)
            return { allowed, tostring(tokens) }
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision tryAcquire(String key, Policy policy) {
        List<?> result = redis.execute(TOKEN_BUCKET, List.of(KEY_PREFIX + key),
                String.valueOf(policy.capacity()), String.valueOf(policy.refillPerSecond()));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Unexpected rate limit script result: " + result);
        }
        boolean allowed = ((Number) result.get(0)).longValue() == 1L;
        double tokensLeft = Double.parseDouble(String.valueOf(result.get(1)));
        return Decision.of(allowed, tokensLeft, policy);
    }
}
