package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public interface TokenIssuer {

    IssuedToken issue(TenantId tenantId, Username username, Set<String> roles, Set<String> authorities);

    /** 給 Gateway 驗簽用的公鑰集合（JWKS 格式）。 */
    Map<String, Object> publicJwks();

    record IssuedToken(String token, Instant expiresAt) {
    }
}
