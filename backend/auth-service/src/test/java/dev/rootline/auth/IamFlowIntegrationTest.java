package dev.rootline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rootline.auth.application.domain.permission.PermissionDefinition;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.PermissionDictionary;
import dev.rootline.auth.application.service.RoleCommandService;
import dev.rootline.auth.application.service.UserCommandService;
import dev.rootline.auth.infra.persistence.TenantRegistryEntity;
import dev.rootline.auth.infra.persistence.TenantRegistryJpaRepository;
import dev.rootline.kernel.security.RootlineHeaders;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 端到端走一遍 IAM 流程。每個測試用隨機租戶，彼此不互相干擾。
 * <p>
 * M2 之前還沒有 Gateway，所以 {@link #asGateway} 模擬 Gateway 的工作：
 * 用 JWKS 公鑰驗 JWT，再把 claims 轉成下游服務信任的 header。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IamFlowIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin-pass-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TenantRegistryJpaRepository tenants;
    @Autowired
    PermissionDictionary dictionary;
    @Autowired
    RoleCommandService roleCommands;
    @Autowired
    UserCommandService userCommands;

    String tenant;

    @BeforeEach
    void seedTenant() {
        tenant = "t" + HexFormat.of().formatHex(new byte[]{(byte) new Random().nextInt(), (byte) new Random().nextInt(),
                (byte) new Random().nextInt(), (byte) new Random().nextInt()});
        seed(tenant);
    }

    private void seed(String tenantCode) {
        TenantId id = TenantId.of(tenantCode);
        tenants.save(new TenantRegistryEntity(tenantCode, TenantRegistryEntity.ACTIVE));
        dictionary.upsertIfNewer(new PermissionDefinition(id, PermissionKey.parse("IAM:USER_READ"), "檢視使用者", "", 1));
        dictionary.upsertIfNewer(new PermissionDefinition(id, PermissionKey.parse("IAM:GROUP_READ"), "檢視群組", "", 1));
        roleCommands.createSystemRoot(id, "TENANT_ADMIN", "租戶管理員", Set.of(PermissionKey.SUPER_ADMIN));
        userCommands.create(id, "admin", "admin@" + tenantCode + ".example", ADMIN_PASSWORD);
        userCommands.assignRole(id, "admin", "TENANT_ADMIN");
    }

    @Test
    void registeredUserCanLoginButHasNoPermissionsYet() throws Exception {
        register("mia", "Mia-pass-123").andExpect(status().isCreated());

        String token = loginToken(tenant, "mia", "Mia-pass-123");
        Claims claims = verify(token);
        assertThat(claims.getSubject()).isEqualTo("mia");
        assertThat(claims.get("tid", String.class)).isEqualTo(tenant);
        assertThat(claims.get("perms", List.class)).isEmpty();

        mvc.perform(asGateway(get("/api/users/mia/permissions-context"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("mia"))
                .andExpect(jsonPath("$.data.personalRoles").isEmpty());

        mvc.perform(asGateway(get("/api/users"), token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void permissionsFlowThroughGroupsIntoTheToken() throws Exception {
        register("mia", "Mia-pass-123").andExpect(status().isCreated());
        String admin = loginToken(tenant, "admin", ADMIN_PASSWORD);

        send(admin, post("/api/roles"), Map.of("roleCode", "hr_viewer", "roleName", "人資檢視"))
                .andExpect(status().isCreated());
        send(admin, post("/api/roles/HR_VIEWER/permissions"), Map.of("systemCode", "IAM", "permissionCode", "USER_READ"))
                .andExpect(status().isOk());
        send(admin, post("/api/groups"), Map.of("groupCode", "finance_team", "groupName", "財務部"))
                .andExpect(status().isCreated());
        mvc.perform(asGateway(post("/api/groups/FINANCE_TEAM/roles/HR_VIEWER"), admin)).andExpect(status().isOk());
        mvc.perform(asGateway(post("/api/groups/FINANCE_TEAM/members/mia"), admin)).andExpect(status().isOk());

        String mia = loginToken(tenant, "mia", "Mia-pass-123");
        assertThat(verify(mia).get("perms", List.class)).containsExactly("IAM:USER_READ");

        mvc.perform(asGateway(get("/api/users"), mia)).andExpect(status().isOk());
        mvc.perform(asGateway(get("/api/users/mia/permissions-context"), mia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupRoles[0].groupCode").value("FINANCE_TEAM"))
                .andExpect(jsonPath("$.data.groupRoles[0].roleCodes[0]").value("HR_VIEWER"))
                .andExpect(jsonPath("$.data.permissions[0].permissionName").value("檢視使用者"));
    }

    @Test
    void accountLocksAfterFiveFailuresAndAdminCanUnlock() throws Exception {
        register("leo", "Leo-pass-123").andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            login(tenant, "leo", "wrong-password").andExpect(status().isUnauthorized());
        }
        login(tenant, "leo", "Leo-pass-123")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        String admin = loginToken(tenant, "admin", ADMIN_PASSWORD);
        mvc.perform(asGateway(post("/api/users/leo/unlock"), admin)).andExpect(status().isOk());
        login(tenant, "leo", "Leo-pass-123").andExpect(status().isOk());
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        register("mia", "Mia-pass-123").andExpect(status().isCreated());

        String wrongPassword = login(tenant, "mia", "nope-nope-nope").andReturn().getResponse().getContentAsString();
        String unknownUser = login(tenant, "ghost", "Mia-pass-123").andReturn().getResponse().getContentAsString();
        String unknownTenant = login("no-such-tenant", "mia", "Mia-pass-123").andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser).isEqualTo(unknownTenant).contains("INVALID_CREDENTIALS");
    }

    @Test
    void tenantsAreIsolated() throws Exception {
        String otherTenant = tenant + "-b";
        seed(otherTenant);
        String adminA = loginToken(tenant, "admin", ADMIN_PASSWORD);

        send(adminA, post("/api/users"), Map.of("username", "only-in-a", "email", "a@a.example", "password", "Only-a-123"))
                .andExpect(status().isCreated());

        String adminB = loginToken(otherTenant, "admin", ADMIN_PASSWORD);
        mvc.perform(asGateway(get("/api/users/only-in-a"), adminB)).andExpect(status().isNotFound());
        mvc.perform(asGateway(get("/api/users/only-in-a"), adminA)).andExpect(status().isOk());
    }

    @Test
    void domainRulesSurfaceAsHttpErrors() throws Exception {
        String admin = loginToken(tenant, "admin", ADMIN_PASSWORD);

        send(admin, post("/api/roles"), Map.of("roleCode", "AUDITOR", "roleName", "稽核")).andExpect(status().isCreated());
        send(admin, post("/api/roles/AUDITOR/permissions"), Map.of("systemCode", "DEPT", "permissionCode", "TREE_WRITE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_DEFINED"));

        send(admin, put("/api/roles/TENANT_ADMIN/name"), Map.of("name", "Hijacked"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SYSTEM_ROLE_IMMUTABLE"));

        send(admin, post("/api/roles"), Map.of("roleCode", "AUDITOR", "roleName", "重複")).andExpect(status().isConflict());

        mvc.perform(asGateway(delete("/api/users/admin"), admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    void selfPasswordChangeRequiresCurrentPassword() throws Exception {
        register("mia", "Mia-pass-123").andExpect(status().isCreated());
        String mia = loginToken(tenant, "mia", "Mia-pass-123");

        send(mia, put("/api/users/mia/password"), Map.of("currentPassword", "wrong", "newPassword", "New-pass-456"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
        send(mia, put("/api/users/mia/password"), Map.of("currentPassword", "Mia-pass-123", "newPassword", "New-pass-456"))
                .andExpect(status().isOk());

        login(tenant, "mia", "New-pass-456").andExpect(status().isOk());
        send(mia, put("/api/users/admin/password"), Map.of("newPassword", "Pwned-123456"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedUserCannotLogin() throws Exception {
        register("mia", "Mia-pass-123").andExpect(status().isCreated());
        String admin = loginToken(tenant, "admin", ADMIN_PASSWORD);

        mvc.perform(asGateway(delete("/api/users/mia"), admin)).andExpect(status().isOk());
        login(tenant, "mia", "Mia-pass-123").andExpect(status().isUnauthorized());

        mvc.perform(asGateway(post("/api/users/mia/reactivate"), admin)).andExpect(status().isOk());
        login(tenant, "mia", "Mia-pass-123").andExpect(status().isOk());
    }

    @Test
    void registrationWritesOutboxEventWithoutSecrets() throws Exception {
        register("mia", "Mia-pass-123").andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT topic, status, payload::text AS payload FROM outbox_events
                WHERE event_type = 'UserRegistered' AND payload->>'tenantId' = ? AND payload->>'username' = 'mia'
                """, tenant);
        assertThat(row.get("topic")).isEqualTo("rootline.auth.user");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat((String) row.get("payload")).contains("mia@").doesNotContain("$2a$").doesNotContain("Mia-pass");
    }

    @Test
    void registrationRequiresActiveTenant() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("tenantCode", "unknown-tenant", "username", "mia",
                                "email", "mia@x.example", "password", "Mia-pass-123"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_AVAILABLE"));
    }

    // ---------- helpers ----------

    private ResultActions register(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("tenantCode", tenant, "username", username,
                        "email", username + "@" + tenant + ".example", "password", password))));
    }

    private ResultActions login(String tenantCode, String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("tenantCode", tenantCode, "username", username,
                        "password", password))));
    }

    private String loginToken(String tenantCode, String username, String password) throws Exception {
        String body = login(tenantCode, username, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asString();
    }

    private ResultActions send(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(asGateway(request, token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    /** 模擬 Gateway：驗 JWT 後把 claims 轉成 header。 */
    private MockHttpServletRequestBuilder asGateway(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        Claims claims = verify(token);
        @SuppressWarnings("unchecked")
        List<String> perms = claims.get("perms", List.class);
        return request
                .header(RootlineHeaders.TENANT_ID, claims.get("tid", String.class))
                .header(RootlineHeaders.USER_ID, claims.getSubject())
                .header(RootlineHeaders.USER_PERMISSIONS, String.join(",", perms));
    }

    /** 只用 JWKS 公開的資訊驗簽，證明 Gateway 拿 JWKS 就能獨立驗證。 */
    private Claims verify(String token) throws Exception {
        String body = mvc.perform(get("/api/auth/.well-known/jwks.json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode jwk = json.readTree(body).path("keys").get(0);
        Base64.Decoder decoder = Base64.getUrlDecoder();
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                new BigInteger(1, decoder.decode(jwk.path("n").asString())),
                new BigInteger(1, decoder.decode(jwk.path("e").asString()))));
        return Jwts.parser().verifyWith(key).requireIssuer("rootline-auth").build()
                .parseSignedClaims(token).getPayload();
    }
}
