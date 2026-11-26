package dev.rootline.dept.infra.rule;

import dev.rootline.dept.application.domain.rule.ApiRule;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.ApiRuleCachePublisher;
import dev.rootline.kernel.security.RedisDynamicRuleSource;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 整份覆寫租戶的規則 key（格式與 shared-kernel 的 {@link RedisDynamicRuleSource} 約定一致）。
 * 不設過期：資料庫才是權威來源，這裡永遠是最後一次 commit 後的完整副本。
 */
@Component
class RedisApiRuleCachePublisher implements ApiRuleCachePublisher {

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    RedisApiRuleCachePublisher(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void publish(TenantId tenantId, List<ApiRule> enabledRules) {
        List<Map<String, String>> payload = enabledRules.stream()
                .map(r -> Map.of("method", r.method(), "pattern", r.pathPattern(), "permission", r.requiredPermission()))
                .toList();
        redis.opsForValue().set(RedisDynamicRuleSource.KEY_PREFIX + tenantId.value(), json.writeValueAsString(payload));
    }
}
