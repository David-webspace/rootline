package dev.rootline.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class RedisDynamicRuleSourceTest {

    private static final String KEY = RedisDynamicRuleSource.KEY_PREFIX + "northwind";
    private static final String RULES = """
            [{"method":"GET","pattern":"/api/departments/*/tree","permission":"DEPT:SECRET_TREE"},
             {"method":"*","pattern":"/api/users/**","permission":"IAM:PII_ACCESS"}]
            """;

    private final AtomicLong now = new AtomicLong(1_000_000);
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisDynamicRuleSource source;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        source = new RedisDynamicRuleSource(redis, JsonMapper.builder().build(), Duration.ofSeconds(30), now::get);
    }

    @Test
    void matchesMethodAndAntPattern() {
        when(values.get(KEY)).thenReturn(RULES);

        assertThat(source.requiredPermissions("northwind", "GET", "/api/departments/HQ/tree"))
                .containsExactly("DEPT:SECRET_TREE");
        assertThat(source.requiredPermissions("northwind", "POST", "/api/departments/HQ/tree")).isEmpty();
        assertThat(source.requiredPermissions("northwind", "DELETE", "/api/users/mia/roles/X"))
                .containsExactly("IAM:PII_ACCESS");
        assertThat(source.requiredPermissions("northwind", "GET", "/api/roles")).isEmpty();
    }

    @Test
    void cachesPerTenantWithinTtl() {
        when(values.get(KEY)).thenReturn(RULES);
        source.requiredPermissions("northwind", "GET", "/api/roles");
        source.requiredPermissions("northwind", "GET", "/api/roles");
        verify(values, times(1)).get(KEY);

        now.addAndGet(31_000);
        source.requiredPermissions("northwind", "GET", "/api/roles");
        verify(values, times(2)).get(KEY);
    }

    @Test
    void keepsLastKnownRulesWhenRedisFails() {
        when(values.get(KEY)).thenReturn(RULES);
        source.requiredPermissions("northwind", "GET", "/api/users");

        now.addAndGet(31_000);
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(source.requiredPermissions("northwind", "GET", "/api/users")).containsExactly("IAM:PII_ACCESS");
    }

    @Test
    void noRulesWhenNothingWasEverLoaded() {
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        assertThat(source.requiredPermissions("northwind", "GET", "/api/users")).isEmpty();
    }
}
