package dev.rootline.auth.infra.security;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * RS256 簽章金鑰。私鑰只留在 auth-service，Gateway 透過 JWKS 拿公鑰驗簽，
 * 兩邊不需要共用任何 secret。kid 用 RFC 7638 thumbprint，換鑰時 Gateway 能分辨新舊。
 */
final class RsaSigningKey {

    private static final Logger log = LoggerFactory.getLogger(RsaSigningKey.class);
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    private RsaSigningKey(RSAPrivateCrtKey privateKey, RSAPublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keyId = thumbprint(publicKey);
    }

    static RsaSigningKey load(JwtProperties properties) {
        if (properties.privateKeyLocation() != null) {
            Resource resource = new DefaultResourceLoader().getResource(properties.privateKeyLocation());
            if (!resource.exists()) {
                throw new IllegalStateException("JWT private key not found at " + properties.privateKeyLocation());
            }
            return fromPem(resource);
        }
        if (!properties.allowEphemeralKey()) {
            throw new IllegalStateException(
                    "rootline.jwt.private-key-location is not set. Provide an RSA key, or set "
                            + "rootline.jwt.allow-ephemeral-key=true for local development only.");
        }
        log.warn("No JWT private key configured; generating an ephemeral RSA key. "
                + "Tokens become invalid on restart. Do not use this outside local development.");
        return generate();
    }

    private static RsaSigningKey fromPem(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            if (!pem.contains("BEGIN PRIVATE KEY")) {
                throw new IllegalStateException("JWT private key must be PKCS#8 PEM (BEGIN PRIVATE KEY)");
            }
            String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            if (privateKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT RSA key must be at least 2048 bits");
            }
            return new RsaSigningKey(privateKey, publicKey);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot load JWT private key", e);
        }
    }

    private static RsaSigningKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RsaSigningKey((RSAPrivateCrtKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate RSA key", e);
        }
    }

    RSAPrivateCrtKey privateKey() {
        return privateKey;
    }

    RSAPublicKey publicKey() {
        return publicKey;
    }

    String keyId() {
        return keyId;
    }

    Map<String, Object> jwks() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", base64Url(publicKey.getModulus()));
        jwk.put("e", base64Url(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(jwk));
    }

    private static String thumbprint(RSAPublicKey key) {
        String canonical = "{\"e\":\"" + base64Url(key.getPublicExponent()) + "\",\"kty\":\"RSA\",\"n\":\""
                + base64Url(key.getModulus()) + "\"}";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return B64URL.encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** JWK 要求大整數用不帶正負號、不含前導 0 的 big-endian bytes。 */
    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return B64URL.encodeToString(bytes);
    }
}
