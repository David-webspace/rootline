package dev.rootline.gateway.config;

import dev.rootline.gateway.ratelimit.FailoverRateLimiter;
import dev.rootline.gateway.ratelimit.LocalTokenBucketRateLimiter;
import dev.rootline.gateway.ratelimit.RateLimitFilter;
import dev.rootline.gateway.ratelimit.RateLimiter;
import dev.rootline.gateway.ratelimit.RedisTokenBucketRateLimiter;
import dev.rootline.gateway.security.AuthenticationFilter;
import dev.rootline.gateway.security.JwtVerifier;
import dev.rootline.gateway.trace.TraceIdFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Filter 順序固定為：trace id → 身分驗證 → 限流。
 * 限流放在驗證之後，才能用 tenant + user 當 key；無效 token 在驗證階段就被 401 擋下，不消耗下游資源。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class FilterConfig {

    @Bean
    RateLimiter rateLimiter(StringRedisTemplate redis) {
        return new FailoverRateLimiter(new RedisTokenBucketRateLimiter(redis), new LocalTokenBucketRateLimiter());
    }

    @Bean
    FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>(new TraceIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    FilterRegistrationBean<AuthenticationFilter> authenticationFilter(JwtVerifier verifier,
                                                                      GatewayProperties properties) {
        FilterRegistrationBean<AuthenticationFilter> bean =
                new FilterRegistrationBean<>(new AuthenticationFilter(verifier, properties));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter, GatewayProperties properties) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(new RateLimitFilter(limiter, properties));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return bean;
    }
}
