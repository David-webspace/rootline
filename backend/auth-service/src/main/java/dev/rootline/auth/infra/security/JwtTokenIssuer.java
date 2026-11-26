package dev.rootline.auth.infra.security;

import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.TokenIssuer;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 簽發 RS256 JWT。claims：
 * <ul>
 *   <li>{@code sub}：username</li>
 *   <li>{@code tid}：tenant id</li>
 *   <li>{@code roles}：有效角色代碼</li>
 *   <li>{@code perms}：展開後的權限字串（Gateway 轉成 X-User-Permissions）</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(JwtProperties.class)
class JwtTokenIssuer implements TokenIssuer {

    static final String CLAIM_TENANT = "tid";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_PERMISSIONS = "perms";

    private final JwtProperties properties;
    private final RsaSigningKey key;

    JwtTokenIssuer(JwtProperties properties) {
        this.properties = properties;
        this.key = RsaSigningKey.load(properties);
    }

    @Override
    public IssuedToken issue(TenantId tenantId, Username username, Set<String> roles, Set<String> authorities) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        String token = Jwts.builder()
                .header().keyId(key.keyId()).and()
                .issuer(properties.issuer())
                .subject(username.value())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TENANT, tenantId.value())
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_PERMISSIONS, List.copyOf(authorities))
                .signWith(key.privateKey(), Jwts.SIG.RS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public Map<String, Object> publicJwks() {
        return key.jwks();
    }
}
