package dev.rootline.kernel.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.AntPathMatcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 從 Redis 讀租戶的動態規則（dept-tree-service 負責寫入），本機快取 TTL 秒數，避免每個請求都打 Redis。
 * <p>
 * Redis 讀取失敗時沿用上一次的快取；連一次都沒成功過就視為沒有動態規則。
 * 因為動態規則只會收緊權限，失效時退回「只檢查靜態權限」，不會讓人越權。
 */
public class RedisDynamicRuleSource implements DynamicRulePort {

    public static final String KEY_PREFIX = "rootline:api-rules:";

    private static final Logger log = LoggerFactory.getLogger(RedisDynamicRuleSource.class);
    private static final long WARN_INTERVAL_MILLIS = 60_000;

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastWarn = new AtomicLong();

    public RedisDynamicRuleSource(StringRedisTemplate redis, JsonMapper json, Duration ttl) {
        this(redis, json, ttl, System::currentTimeMillis);
    }

    RedisDynamicRuleSource(StringRedisTemplate redis, JsonMapper json, Duration ttl, LongSupplier clock) {
        this.redis = redis;
        this.json = json;
        this.ttlMillis = ttl.toMillis();
        this.clock = clock;
    }

    @Override
    public List<String> requiredPermissions(String tenantId, String httpMethod, String path) {
        List<String> required = new ArrayList<>();
        for (Rule rule : rulesOf(tenantId)) {
            boolean methodMatches = "*".equals(rule.method()) || rule.method().equalsIgnoreCase(httpMethod);
            if (methodMatches && matcher.match(rule.pattern(), path) && !required.contains(rule.permission())) {
                required.add(rule.permission());
            }
        }
        return required;
    }

    private List<Rule> rulesOf(String tenantId) {
        long now = clock.getAsLong();
        Cached cached = cache.get(tenantId);
        if (cached != null && now - cached.loadedAt() < ttlMillis) {
            return cached.rules();
        }
        try {
            List<Rule> rules = parse(redis.opsForValue().get(KEY_PREFIX + tenantId));
            cache.put(tenantId, new Cached(rules, now));
            return rules;
        } catch (RuntimeException e) {
            long previous = lastWarn.get();
            if (now - previous > WARN_INTERVAL_MILLIS && lastWarn.compareAndSet(previous, now)) {
                log.warn("Cannot load dynamic API rules for tenant {} ({}); using {} cached rule(s)", tenantId,
                        e.toString(), cached == null ? 0 : cached.rules().size());
            }
            return cached == null ? List.of() : cached.rules();
        }
    }

    private List<Rule> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Rule> rules = new ArrayList<>();
        for (JsonNode node : json.readTree(value)) {
            rules.add(new Rule(node.path("method").asString("*"), node.path("pattern").asString(),
                    node.path("permission").asString()));
        }
        return List.copyOf(rules);
    }

    record Rule(String method, String pattern, String permission) {
    }

    private record Cached(List<Rule> rules, long loadedAt) {
    }
}
