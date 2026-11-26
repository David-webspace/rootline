package dev.rootline.dept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rootline.kernel.security.RedisDynamicRuleSource;
import dev.rootline.kernel.security.RootlineHeaders;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 權限定義（版本號與 outbox）與動態 API 規則（DB → Redis → 權限攔截器）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PermissionsAndApiRulesIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StringRedisTemplate redis;

    String tenant;

    @BeforeEach
    void tenant() throws Exception {
        tenant = "t" + UUID.randomUUID().toString().substring(0, 8);
        send(admin(post("/api/departments")), Map.of("id", "HQ", "code", "HQ", "name", "Northwind Labs"))
                .andExpect(status().isCreated());
    }

    @Test
    void permissionDefinitionsAreVersionedAndWrittenToTheOutbox() throws Exception {
        JsonNode defined = data(send(admin(post("/api/departments/permissions")),
                Map.of("systemCode", "dept", "permissionCode", "budget_view", "name", "檢視預算"))
                .andExpect(status().isCreated()));
        assertThat(defined.path("version").asLong()).isEqualTo(1);
        assertThat(defined.path("permissionCode").asString()).isEqualTo("BUDGET_VIEW");

        JsonNode updated = data(send(admin(put("/api/departments/permissions/DEPT/BUDGET_VIEW")),
                Map.of("name", "檢視部門預算", "description", "含年度預算與實支")));
        assertThat(updated.path("version").asLong()).isEqualTo(2);

        // 內容沒變不算異動：版本不動、不發事件
        JsonNode unchanged = data(send(admin(put("/api/departments/permissions/DEPT/BUDGET_VIEW")),
                Map.of("name", "檢視部門預算", "description", "含年度預算與實支")));
        assertThat(unchanged.path("version").asLong()).isEqualTo(2);

        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT event_type, topic, payload->>'version' AS version FROM outbox_events
                WHERE aggregate_type = 'Permission' AND payload->>'tenantId' = ? ORDER BY seq
                """, tenant);
        assertThat(events).extracting(e -> e.get("event_type") + "@" + e.get("version"))
                .containsExactly("PermissionDefined@1", "PermissionUpdated@2");
        assertThat(events).allSatisfy(e -> assertThat(e.get("topic")).isEqualTo("rootline.dept.permission"));

        send(admin(post("/api/departments/permissions")),
                Map.of("systemCode", "DEPT", "permissionCode", "BUDGET_VIEW", "name", "重複"))
                .andExpect(status().isConflict());
        send(admin(post("/api/departments/permissions")),
                Map.of("systemCode", "DEPT", "permissionCode", "ADMIN_ALL", "name", "想偷用萬用權限"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERMISSION_CODE_RESERVED"));
    }

    @Test
    void apiRuleTightensAccessUntilDisabled() throws Exception {
        MockHttpServletRequestBuilder readTree = viewer(get("/api/departments/HQ/tree"), "DEPT:TREE_READ");
        mvc.perform(readTree).andExpect(status().isOk());

        JsonNode rule = data(send(admin(post("/api/departments/api-rules")),
                Map.of("method", "get", "pathPattern", "/api/departments/*/tree",
                        "requiredPermission", "dept:confidential_tree", "description", "組織樹列為機密"))
                .andExpect(status().isCreated()));
        String ruleId = rule.path("id").asString();

        String cached = redis.opsForValue().get(RedisDynamicRuleSource.KEY_PREFIX + tenant);
        assertThat(cached).contains("/api/departments/*/tree").contains("DEPT:CONFIDENTIAL_TREE");

        mvc.perform(viewer(get("/api/departments/HQ/tree"), "DEPT:TREE_READ"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tenant policy requires permission DEPT:CONFIDENTIAL_TREE"));
        mvc.perform(viewer(get("/api/departments/HQ/tree"), "DEPT:TREE_READ,DEPT:CONFIDENTIAL_TREE"))
                .andExpect(status().isOk());
        // 規則只收緊：有機密權限但沒有基本的 TREE_READ 仍然不行
        mvc.perform(viewer(get("/api/departments/HQ/tree"), "DEPT:CONFIDENTIAL_TREE"))
                .andExpect(status().isForbidden());
        // 其他 API 不受影響
        mvc.perform(viewer(get("/api/departments/HQ/breadcrumbs"), "DEPT:TREE_READ")).andExpect(status().isOk());

        send(admin(patch("/api/departments/api-rules/" + ruleId + "/status")), Map.of("enabled", false))
                .andExpect(status().isOk());
        mvc.perform(viewer(get("/api/departments/HQ/tree"), "DEPT:TREE_READ")).andExpect(status().isOk());
    }

    @Test
    void rulesAreIsolatedPerTenant() throws Exception {
        send(admin(post("/api/departments/api-rules")), Map.of("method", "*", "pathPattern", "/api/departments/**",
                "requiredPermission", "DEPT:LOCKDOWN")).andExpect(status().isCreated());

        mvc.perform(get("/api/departments/roots")
                        .header(RootlineHeaders.TENANT_ID, "some-other-tenant").header(RootlineHeaders.USER_ID, "x")
                        .header(RootlineHeaders.USER_PERMISSIONS, "DEPT:TREE_READ"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidRulesAreRejected() throws Exception {
        send(admin(post("/api/departments/api-rules")), Map.of("method", "TRACE", "pathPattern", "/api/x",
                "requiredPermission", "DEPT:X")).andExpect(jsonPath("$.code").value("API_RULE_METHOD_INVALID"));
        send(admin(post("/api/departments/api-rules")), Map.of("method", "GET", "pathPattern", "/actuator/**",
                "requiredPermission", "DEPT:X")).andExpect(jsonPath("$.code").value("API_RULE_PATH_INVALID"));
        send(admin(post("/api/departments/api-rules")), Map.of("method", "GET", "pathPattern", "/api/x",
                "requiredPermission", "no-colon")).andExpect(jsonPath("$.code").value("API_RULE_PERMISSION_INVALID"));
    }

    @Test
    void republishRebuildsTheCacheFromTheDatabase() throws Exception {
        send(admin(post("/api/departments/api-rules")), Map.of("method", "GET", "pathPattern", "/api/departments/roots",
                "requiredPermission", "DEPT:ROOTS_VIEW")).andExpect(status().isCreated());
        redis.delete(RedisDynamicRuleSource.KEY_PREFIX + tenant);

        mvc.perform(admin(post("/api/departments/api-rules/publish"))).andExpect(jsonPath("$.data").value(1));
        assertThat(redis.opsForValue().get(RedisDynamicRuleSource.KEY_PREFIX + tenant)).contains("DEPT:ROOTS_VIEW");
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header(RootlineHeaders.TENANT_ID, tenant)
                .header(RootlineHeaders.USER_ID, "admin")
                .header(RootlineHeaders.USER_PERMISSIONS, "DEPT:ADMIN_ALL");
    }

    private MockHttpServletRequestBuilder viewer(MockHttpServletRequestBuilder request, String permissions) {
        return request.header(RootlineHeaders.TENANT_ID, tenant)
                .header(RootlineHeaders.USER_ID, "viewer")
                .header(RootlineHeaders.USER_PERMISSIONS, permissions);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }
}
