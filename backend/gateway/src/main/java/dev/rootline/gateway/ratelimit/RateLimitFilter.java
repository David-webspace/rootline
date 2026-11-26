package dev.rootline.gateway.ratelimit;

import dev.rootline.gateway.config.GatewayProperties;
import dev.rootline.gateway.config.GatewayProperties.Policy;
import dev.rootline.gateway.ratelimit.RateLimiter.Decision;
import dev.rootline.gateway.security.AuthenticationFilter;
import dev.rootline.gateway.security.JwtVerifier.VerifiedCaller;
import dev.rootline.gateway.web.GatewayResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 依呼叫者身分挑選限流桶：
 * <ul>
 *   <li>登入 / 註冊：以 IP 計，配額最嚴（擋暴力破解與大量註冊）</li>
 *   <li>已驗證：以 tenant + user 計，同一個人換 IP 也共用額度</li>
 *   <li>其他匿名請求：以 IP 計</li>
 * </ul>
 * IP 取自 TCP 連線端（{@code getRemoteAddr}），不信任 client 自填的 X-Forwarded-For；
 * 若前面另有可信的 reverse proxy，應在 Tomcat 設定 RemoteIpValve 而不是在這裡解析 header。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> CREDENTIAL_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final RateLimiter limiter;
    private final GatewayProperties.RateLimit config;

    public RateLimitFilter(RateLimiter limiter, GatewayProperties properties) {
        this.limiter = limiter;
        this.config = properties.rateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return config == null || !config.enabled() || !request.getServletPath().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Object caller = request.getAttribute(AuthenticationFilter.CALLER_ATTRIBUTE);

        String key;
        Policy policy;
        if (CREDENTIAL_PATHS.contains(request.getServletPath())) {
            key = "login:" + ip;
            policy = config.login();
        } else if (caller instanceof VerifiedCaller verified) {
            key = "user:" + verified.tenantId() + ":" + verified.userId();
            policy = config.authenticated();
        } else {
            key = "ip:" + ip;
            policy = config.anonymous();
        }

        Decision decision = limiter.tryAcquire(key, policy);
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            GatewayResponses.write(response, 429, "RATE_LIMITED",
                    "Too many requests; retry after " + decision.retryAfterSeconds() + "s");
            return;
        }
        chain.doFilter(request, response);
    }
}
