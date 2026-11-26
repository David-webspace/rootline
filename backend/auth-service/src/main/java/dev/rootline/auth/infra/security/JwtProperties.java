package dev.rootline.auth.infra.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param issuer             JWT 的 iss
 * @param ttl                token 有效期
 * @param privateKeyLocation PKCS#8 PEM 格式的 RSA 私鑰位置，例如 {@code file:/run/secrets/jwt.pem}
 * @param allowEphemeralKey  沒有設定私鑰時，是否允許啟動時臨時產生一把（重啟後舊 token 全部失效）。
 *                           只該在本機開發與測試打開。
 */
@ConfigurationProperties("rootline.jwt")
public record JwtProperties(String issuer, Duration ttl, String privateKeyLocation, boolean allowEphemeralKey) {

    public JwtProperties {
        issuer = issuer == null || issuer.isBlank() ? "rootline-auth" : issuer;
        ttl = ttl == null ? Duration.ofHours(8) : ttl;
        privateKeyLocation = privateKeyLocation == null || privateKeyLocation.isBlank() ? null : privateKeyLocation.trim();
    }
}
