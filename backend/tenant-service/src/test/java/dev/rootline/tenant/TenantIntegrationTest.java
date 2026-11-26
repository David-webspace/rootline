package dev.rootline.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rootline.kernel.security.RootlineHeaders;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantIntegrationTest {

    private static final String PASSWORD = "Initial-pass-2026";

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void provisioningPublishesHashedPasswordOnly() throws Exception {
        String id = newTenantId();
        provision(id).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.plan").value("PRO"));

        Map<String, Object> event = jdbc.queryForMap("""
                SELECT topic, payload::text AS payload, payload->>'adminUsername' AS admin, payload->>'adminPasswordHash' AS hash
                FROM outbox_events WHERE event_type = 'TenantProvisioned' AND aggregate_id = ?
                """, id);
        assertThat(event.get("topic")).isEqualTo("rootline.tenant.lifecycle");
        assertThat((String) event.get("payload")).doesNotContain(PASSWORD);
        assertThat(event.get("admin")).isEqualTo("owner");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, (String) event.get("hash"))).isTrue();
    }

    @Test
    void lifecycleEventsAreOrderedPerTenant() throws Exception {
        String id = newTenantId();
        provision(id);
        send(post("/api/platform/tenants/" + id + "/suspend"), Map.of("reason", "逾期未付款")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        send(put("/api/platform/tenants/" + id + "/plan"), Map.of("plan", "ENTERPRISE"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_ACTIVE"));
        mvc.perform(platform(post("/api/platform/tenants/" + id + "/reactivate"))).andExpect(status().isOk());
        send(put("/api/platform/tenants/" + id + "/plan"), Map.of("plan", "ENTERPRISE")).andExpect(status().isOk());
        send(put("/api/platform/tenants/" + id + "/plan"), Map.of("plan", "FREE"))
                .andExpect(jsonPath("$.code").value("PLAN_NOT_UPGRADE"));

        List<String> types = jdbc.queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ? ORDER BY seq", String.class, id);
        assertThat(types).containsExactly("TenantProvisioned", "TenantSuspended", "TenantReactivated", "TenantPlanUpgraded");
    }

    @Test
    void onlyPlatformOperatorsCanManageTenants() throws Exception {
        mvc.perform(post("/api/platform/tenants").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body(newTenantId())))
                        .header(RootlineHeaders.TENANT_ID, "northwind").header(RootlineHeaders.USER_ID, "admin")
                        .header(RootlineHeaders.USER_PERMISSIONS, "*:ADMIN_ALL"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PLATFORM_ONLY"));
    }

    @Test
    void reservedAndDuplicateIdsAreRejected() throws Exception {
        send(post("/api/platform/tenants"), body("platform")).andExpect(jsonPath("$.code").value("TENANT_ID_RESERVED"));
        String id = newTenantId();
        provision(id).andExpect(status().isCreated());
        provision(id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TENANT_ID_TAKEN"));

        Map<String, Object> weak = new java.util.HashMap<>(body(newTenantId()));
        weak.put("adminPassword", "short");
        send(post("/api/platform/tenants"), weak).andExpect(jsonPath("$.code").value("PASSWORD_INVALID"));
    }

    @Test
    void searchFiltersByKeywordAndStatus() throws Exception {
        String id = newTenantId();
        provision(id);
        send(post("/api/platform/tenants/" + id + "/suspend"), Map.of("reason", "測試"));

        mvc.perform(platform(get("/api/platform/tenants").param("keyword", id).param("status", "SUSPENDED")))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].suspendReason").value("測試"));
        mvc.perform(platform(get("/api/platform/tenants").param("keyword", id).param("status", "ACTIVE")))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private static String newTenantId() {
        return "acme-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private static Map<String, Object> body(String id) {
        return Map.of("tenantId", id, "companyName", "Acme Robotics", "plan", "pro", "adminUsername", "Owner",
                "adminEmail", "owner@acme.example", "adminPassword", PASSWORD);
    }

    private ResultActions provision(String id) throws Exception {
        return send(post("/api/platform/tenants"), body(id));
    }

    private MockHttpServletRequestBuilder platform(MockHttpServletRequestBuilder request) {
        return request.header(RootlineHeaders.TENANT_ID, "platform").header(RootlineHeaders.USER_ID, "operator")
                .header(RootlineHeaders.USER_PERMISSIONS, "PLATFORM:TENANT_READ,PLATFORM:TENANT_WRITE");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(platform(request).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
