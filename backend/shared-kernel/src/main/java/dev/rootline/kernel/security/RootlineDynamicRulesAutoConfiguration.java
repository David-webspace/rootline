package dev.rootline.kernel.security;

import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code rootline.security.dynamic-rules.enabled=true} 時，用 Redis 裡的租戶規則當第二軌權限。
 * 必須排在 {@link RootlineSecurityAutoConfiguration} 之前，才能取代它預設的「無動態規則」。
 */
@AutoConfiguration(before = RootlineSecurityAutoConfiguration.class,
        afterName = {"org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
                "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "rootline.security.dynamic-rules", name = "enabled", havingValue = "true")
@ConditionalOnBean(StringRedisTemplate.class)
public class RootlineDynamicRulesAutoConfiguration {

    @Bean
    DynamicRulePort redisDynamicRules(StringRedisTemplate redis, JsonMapper json, Environment environment) {
        Duration ttl = environment.getProperty("rootline.security.dynamic-rules.cache-ttl", Duration.class,
                Duration.ofSeconds(30));
        return new RedisDynamicRuleSource(redis, json, ttl);
    }
}
