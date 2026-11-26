package dev.rootline.gateway.security;

import dev.rootline.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import java.security.Key;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 驗證 RS256 JWT 並取出呼叫者身分。驗簽公鑰依 header 的 kid 從 JWKS 找；
 * 找不到 kid、簽章不符、過期、issuer 不對、缺必要 claim，一律視為無效。
 * jjwt 預設拒絕 {@code alg: none}，而 RSA 公鑰也無法拿來驗 HS256，所以不會有演算法混淆攻擊。
 */
@Component
public class JwtVerifier {

    private final JwtParser parser;

    public JwtVerifier(JwksKeyStore keys, GatewayProperties properties) {
        GatewayProperties.Jwt config = properties.jwt();
        this.parser = Jwts.parser()
                .keyLocator(new LocatorAdapter<Key>() {
                    @Override
                    protected Key locate(JwsHeader header) {
                        return keys.find(header.getKeyId())
                                .orElseThrow(() -> new JwtException("Unknown signing key"));
                    }
                })
                .requireIssuer(config.issuer())
                .clockSkewSeconds(config.clockSkew().toSeconds())
                .build();
    }

    public Optional<VerifiedCaller> verify(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String tenant = claims.get("tid", String.class);
            String user = claims.getSubject();
            if (tenant == null || tenant.isBlank() || user == null || user.isBlank()) {
                return Optional.empty();
            }
            List<?> rawPermissions = claims.get("perms", List.class);
            List<String> permissions = rawPermissions == null ? List.of()
                    : rawPermissions.stream().map(String::valueOf).toList();
            return Optional.of(new VerifiedCaller(tenant, user, permissions));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record VerifiedCaller(String tenantId, String userId, List<String> permissions) {
    }
}
