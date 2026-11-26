package dev.rootline.gateway.security;

import dev.rootline.gateway.config.GatewayProperties;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 快取 auth-service 公布的驗簽公鑰（JWKS）。
 * <ul>
 *   <li>第一次需要時才抓，Gateway 啟動不依賴 auth-service 已經在線</li>
 *   <li>遇到未知 kid（auth 換鑰）會重抓，但有最短間隔，避免被亂填 kid 的 token 拿來打爆 auth</li>
 *   <li>重抓失敗時保留舊的金鑰，不讓 auth 短暫不可用拖垮所有已登入使用者</li>
 * </ul>
 */
@Component
public class JwksKeyStore {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyStore.class);

    private final RestClient http;
    private final JsonMapper json;
    private final GatewayProperties.Jwt config;
    private final Clock clock;

    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant lastRefresh = Instant.EPOCH;

    @Autowired
    public JwksKeyStore(RestClient.Builder http, JsonMapper json, GatewayProperties properties) {
        this(http.build(), json, properties.jwt(), Clock.systemUTC());
    }

    JwksKeyStore(RestClient http, JsonMapper json, GatewayProperties.Jwt config, Clock clock) {
        this.http = http;
        this.json = json;
        this.config = config;
        this.clock = clock;
    }

    public Optional<PublicKey> find(String keyId) {
        if (keyId == null) {
            return Optional.empty();
        }
        PublicKey key = keys.get(keyId);
        if (key == null && refreshAllowed()) {
            refresh();
            key = keys.get(keyId);
        }
        return Optional.ofNullable(key);
    }

    private boolean refreshAllowed() {
        return lastRefresh.plus(config.minRefreshInterval()).isBefore(clock.instant());
    }

    private synchronized void refresh() {
        if (!refreshAllowed()) {
            return;
        }
        lastRefresh = clock.instant();
        try {
            String body = http.get().uri(config.jwksUri()).retrieve().body(String.class);
            Map<String, PublicKey> parsed = parse(body);
            if (!parsed.isEmpty()) {
                keys = Map.copyOf(parsed);
                log.info("Loaded {} JWT signing key(s) from {}", parsed.size(), config.jwksUri());
            }
        } catch (RuntimeException e) {
            log.warn("Cannot refresh JWKS from {}; keeping {} cached key(s): {}", config.jwksUri(), keys.size(),
                    e.getMessage());
        }
    }

    private Map<String, PublicKey> parse(String body) {
        Map<String, PublicKey> result = new HashMap<>();
        Base64.Decoder decoder = Base64.getUrlDecoder();
        for (JsonNode jwk : json.readTree(body).path("keys")) {
            if (!"RSA".equals(jwk.path("kty").asString()) || jwk.path("kid").isMissingNode()) {
                continue;
            }
            try {
                PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, decoder.decode(jwk.path("n").asString())),
                        new BigInteger(1, decoder.decode(jwk.path("e").asString()))));
                result.put(jwk.path("kid").asString(), key);
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.warn("Skipping malformed JWK {}: {}", jwk.path("kid").asString(), e.getMessage());
            }
        }
        return result;
    }
}
