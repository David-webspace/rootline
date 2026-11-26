package dev.rootline.gateway.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param services   下游服務的 base URL，key 為 auth / dept / tenant / kyc
 * @param jwt        JWT 驗證設定
 * @param publicPaths 不需要 JWT 的路徑（完全比對，method + path）
 * @param rateLimit  令牌桶限流設定
 */
@ConfigurationProperties("rootline.gateway")
public record GatewayProperties(Map<String, String> services, Jwt jwt, List<PublicPath> publicPaths,
                                RateLimit rateLimit) {

    public GatewayProperties {
        services = services == null ? Map.of() : Map.copyOf(services);
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }

    public String serviceUri(String name) {
        String uri = services.get(name);
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("rootline.gateway.services." + name + " is not configured");
        }
        return uri;
    }

    /**
     * @param jwksUri          auth-service 的 JWKS 位址
     * @param issuer           必須符合的 iss
     * @param clockSkew        容許的時鐘誤差
     * @param minRefreshInterval 遇到未知 kid 時重新抓 JWKS 的最短間隔，避免被假 kid 灌爆 auth-service
     */
    public record Jwt(String jwksUri, String issuer, Duration clockSkew, Duration minRefreshInterval) {

        public Jwt {
            issuer = issuer == null ? "rootline-auth" : issuer;
            clockSkew = clockSkew == null ? Duration.ofSeconds(30) : clockSkew;
            minRefreshInterval = minRefreshInterval == null ? Duration.ofSeconds(10) : minRefreshInterval;
        }
    }

    public record PublicPath(String method, String path) {

        public boolean matches(String requestMethod, String requestPath) {
            return method.equalsIgnoreCase(requestMethod) && path.equals(requestPath);
        }
    }

    /**
     * @param authenticated 已登入使用者，以 tenant + user 為 key
     * @param anonymous     未登入請求，以 IP 為 key
     * @param login         登入 / 註冊，以 IP 為 key，額外更嚴格（擋暴力破解）
     */
    public record RateLimit(boolean enabled, Policy authenticated, Policy anonymous, Policy login) {
    }

    /**
     * @param capacity        桶子容量（允許的瞬間突發量）
     * @param refillPerSecond 每秒補充的 token 數（長期平均速率）
     */
    public record Policy(int capacity, double refillPerSecond) {

        public Policy {
            if (capacity <= 0 || refillPerSecond <= 0) {
                throw new IllegalArgumentException("Rate limit capacity and refill rate must be positive");
            }
        }
    }
}
