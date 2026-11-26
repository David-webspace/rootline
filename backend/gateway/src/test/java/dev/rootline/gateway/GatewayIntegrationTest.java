package dev.rootline.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rootline.gateway.FakeDownstream.SigningKey;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 從外部 HTTP 打真的 Gateway：真的 Redis（限流）、假的下游（回顯收到的 header）、測試用 RSA 金鑰。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

    static FakeDownstream downstream;
    static SigningKey key;
    static int deadPort;

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void startDownstream() throws Exception {
        downstream = new FakeDownstream();
        key = downstream.addKey("key-1");
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
    }

    @AfterAll
    static void stopDownstream() {
        downstream.stop();
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) throws Exception {
        if (downstream == null) {
            startDownstream();
        }
        registry.add("rootline.gateway.services.auth", downstream::baseUrl);
        registry.add("rootline.gateway.services.dept", downstream::baseUrl);
        registry.add("rootline.gateway.services.tenant", downstream::baseUrl);
        registry.add("rootline.gateway.services.kyc", () -> "http://127.0.0.1:" + deadPort);
        registry.add("rootline.gateway.jwt.jwks-uri", () -> downstream.baseUrl() + "/jwks");
        registry.add("rootline.gateway.jwt.min-refresh-interval", () -> "0s");
        registry.add("rootline.gateway.rate-limit.login.capacity", () -> "3");
        registry.add("rootline.gateway.rate-limit.login.refill-per-second", () -> "0.01");
        registry.add("rootline.gateway.rate-limit.authenticated.capacity", () -> "1000");
    }

    @Test
    void publicPathPassesWithoutTokenButForgedIdentityIsStripped() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/auth/.well-known/jwks.json"))
                .header("X-Tenant-Id", "evil-corp")
                .header("x-user-permissions", "*:ADMIN_ALL")
                .GET());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode headers = json.readTree(response.body()).path("headers");
        assertThat(headers.has("x-tenant-id")).isFalse();
        assertThat(headers.has("x-user-permissions")).isFalse();
    }

    @Test
    void protectedPathWithoutTokenIsRejectedBeforeReachingDownstream() throws Exception {
        String path = "/api/users/" + UUID.randomUUID();
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(path)).GET());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(downstream.hits()).doesNotContain(path);
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        SigningKey stranger = new SigningKey("key-1", generator.generateKeyPair()); // 同 kid、不同私鑰
        Instant now = Instant.now();

        List<String> invalid = List.of(
                token(stranger, "northwind", "mia", now.plusSeconds(600), "rootline-auth"),   // 簽章不符
                token(key, "northwind", "mia", now.minusSeconds(600), "rootline-auth"),       // 已過期
                token(key, "northwind", "mia", now.plusSeconds(600), "someone-else"),         // issuer 不對
                token(key, null, "mia", now.plusSeconds(600), "rootline-auth"),               // 沒有 tenant
                "not.a.jwt");

        for (String bad : invalid) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/users"))
                    .header("Authorization", "Bearer " + bad).GET());
            assertThat(response.statusCode()).as(bad).isEqualTo(401);
        }
    }

    @Test
    void validTokenOverridesForgedHeadersWithClaims() throws Exception {
        String user = "mia-" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/users/" + user))
                .header("Authorization", "Bearer " + validToken(user))
                .header("X-TENANT-ID", "evil-corp")
                .header("x-user-id", "admin")
                .header("X-User-Permissions", "*:ADMIN_ALL")
                .GET());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode headers = json.readTree(response.body()).path("headers");
        assertThat(headers.path("x-tenant-id").asString()).isEqualTo("northwind");
        assertThat(headers.path("x-user-id").asString()).isEqualTo(user);
        assertThat(headers.path("x-user-permissions").asString()).isEqualTo("IAM:USER_READ,IAM:GROUP_READ");
        assertThat(response.headers().firstValue("X-RateLimit-Remaining")).isPresent();
    }

    @Test
    void traceIdIsPropagatedAndReturned() throws Exception {
        HttpResponse<String> generated = send(HttpRequest.newBuilder(uri("/api/users/me"))
                .header("Authorization", "Bearer " + validToken("leo")).GET());
        String traceId = generated.headers().firstValue("X-Trace-Id").orElseThrow();
        assertThat(json.readTree(generated.body()).path("headers").path("x-trace-id").asString()).isEqualTo(traceId);

        HttpResponse<String> kept = send(HttpRequest.newBuilder(uri("/api/users/me"))
                .header("Authorization", "Bearer " + validToken("leo"))
                .header("X-Trace-Id", "client-trace-0001").GET());
        assertThat(kept.headers().firstValue("X-Trace-Id")).contains("client-trace-0001");

        HttpResponse<String> replaced = send(HttpRequest.newBuilder(uri("/api/users/me"))
                .header("Authorization", "Bearer " + validToken("leo"))
                .header("X-Trace-Id", "bad id with spaces").GET());
        assertThat(replaced.headers().firstValue("X-Trace-Id")).isPresent().get().isNotEqualTo("bad id with spaces");
    }

    @Test
    void pathTraversalDoesNotBypassAuthentication() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/x/../api/users")).GET());
        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(downstream.hits()).doesNotContain("/api/users");
    }

    @Test
    void unreachableServiceFallsBackTo503() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/kyc/me"))
                .header("Authorization", "Bearer " + validToken("leo")).GET());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void downstreamApplicationErrorsPassThroughUntouched() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/departments/boom"))
                .header("Authorization", "Bearer " + validToken("leo")).GET());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(json.readTree(response.body()).path("code").asString()).isEqualTo("DOWNSTREAM_BUG");
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(send(login()).statusCode()).isEqualTo(200);
        }
        HttpResponse<String> limited = send(login());
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(json.readTree(limited.body()).path("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void newSigningKeyIsPickedUpAfterRotation() throws Exception {
        SigningKey rotated = downstream.addKey("key-2-" + UUID.randomUUID());
        String token = token(rotated, "northwind", "leo", Instant.now().plusSeconds(600), "rootline-auth");

        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/users/leo"))
                .header("Authorization", "Bearer " + token).GET());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // ---------- helpers ----------

    private HttpRequest.Builder login() {
        return HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"tenantCode\":\"northwind\"}"));
    }

    private String validToken(String user) {
        return token(key, "northwind", user, Instant.now().plusSeconds(600), "rootline-auth");
    }

    private static String token(SigningKey signingKey, String tenant, String user, Instant expiresAt, String issuer) {
        var builder = Jwts.builder()
                .header().keyId(signingKey.kid()).and()
                .issuer(issuer)
                .subject(user)
                .issuedAt(Date.from(expiresAt.minusSeconds(1200)))
                .expiration(Date.from(expiresAt))
                .claim("perms", List.of("IAM:USER_READ", "IAM:GROUP_READ"));
        if (tenant != null) {
            builder.claim("tid", tenant);
        }
        return builder.signWith(signingKey.pair().getPrivate(), Jwts.SIG.RS256).compact();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
