package dev.rootline.dept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentEventStore;
import dev.rootline.dept.application.service.DepartmentCommandService;
import dev.rootline.kernel.security.RootlineHeaders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 組織樹的端到端測試。每個測試一個隨機租戶，先建好這棵樹：
 * <pre>
 * HQ  Northwind Labs
 * ├── ENG  Engineering
 * │   ├── PLATFORM  Platform
 * │   └── MOBILE    Mobile
 * └── SALES  Sales
 *     └── APAC  APAC
 * </pre>
 * 身分 header 直接帶（Gateway 負責驗 JWT，這裡測的是 dept-tree 本身）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DepartmentTreeIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DepartmentCommandService commands;
    @Autowired
    DepartmentEventStore store;
    @Autowired
    TransactionTemplate tx;

    String tenant;

    @BeforeEach
    void buildTree() throws Exception {
        tenant = "t" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> tree = node("HQ", "Northwind Labs",
                node("ENG", "Engineering", node("PLATFORM", "Platform"), node("MOBILE", "Mobile")),
                node("SALES", "Sales", node("APAC", "APAC")));
        send(post("/api/departments/tree"), Map.of("root", tree))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void nestedTreeIsReturnedInOrderWithDepth() throws Exception {
        JsonNode root = data(as(get("/api/departments/HQ/tree")));

        assertThat(root.path("name").asString()).isEqualTo("Northwind Labs");
        assertThat(names(root.path("children"))).containsExactly("Engineering", "Sales");
        JsonNode eng = root.path("children").get(0);
        assertThat(eng.path("depth").asInt()).isEqualTo(1);
        assertThat(names(eng.path("children"))).containsExactly("Mobile", "Platform");
        assertThat(eng.path("children").get(0).path("depth").asInt()).isEqualTo(2);

        // 閉包表列數 = Σ(每個節點的深度 + 1) = 1 + 2 + 2 + 3 + 3 + 3
        assertThat(closureRows()).isEqualTo(14);
    }

    @Test
    void movingASubtreeRewiresAllDescendants() throws Exception {
        send(post("/api/departments/move"), Map.of("departmentId", "ENG", "newParentId", "SALES"))
                .andExpect(status().isOk());

        JsonNode crumbs = data(as(get("/api/departments/PLATFORM/breadcrumbs")));
        assertThat(codes(crumbs)).containsExactly("HQ", "SALES", "ENG", "PLATFORM");
        assertThat(crumbs.get(3).path("depth").asInt()).isZero();

        JsonNode sales = data(as(get("/api/departments/SALES/tree")));
        assertThat(names(sales.path("children"))).containsExactly("APAC", "Engineering");
        // HQ1 + SALES2 + APAC3 + ENG3 + PLATFORM4 + MOBILE4
        assertThat(closureRows()).isEqualTo(17);
    }

    @Test
    void movesThatWouldCreateACycleAreRejected() throws Exception {
        send(post("/api/departments/move"), Map.of("departmentId", "ENG", "newParentId", "PLATFORM"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CYCLE_DETECTED"));
        send(post("/api/departments/move"), Map.of("departmentId", "ENG", "newParentId", "ENG"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CYCLE_DETECTED"));
        assertThat(closureRows()).isEqualTo(14);
    }

    @Test
    void movingToNullPromotesToRoot() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("departmentId", "SALES");
        body.put("newParentId", null);
        send(post("/api/departments/move"), body).andExpect(status().isOk());

        JsonNode roots = data(as(get("/api/departments/roots")));
        assertThat(codes(roots.path("content"))).containsExactlyInAnyOrder("HQ", "SALES");
        assertThat(codes(data(as(get("/api/departments/APAC/breadcrumbs"))))).containsExactly("SALES", "APAC");
    }

    @Test
    void duplicateCodeRollsBackTheWholeBatch() throws Exception {
        send(post("/api/departments/tree"), Map.of("parentId", "HQ",
                "root", node("OPS", "Operations", node("OPS-EU", "EU"), node("eng", "Duplicate of ENG"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_CODE_TAKEN"));

        as(get("/api/departments/search").param("keyword", "Operations"))
                .andExpect(jsonPath("$.data").isEmpty());
        assertThat(closureRows()).isEqualTo(14);
    }

    @Test
    void headcountsRollUpAndBlockDisabling() throws Exception {
        assign("PLATFORM", "mia");
        assign("PLATFORM", "leo");
        assign("ENG", "ava");
        send(post("/api/departments/PLATFORM/employees"), Map.of("employeeId", "MIA"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_ALREADY_ASSIGNED"));

        JsonNode root = data(as(get("/api/departments/HQ/tree")));
        JsonNode eng = root.path("children").get(0);
        assertThat(root.path("totalEmployeeCount").asInt()).isEqualTo(3);
        assertThat(eng.path("directEmployeeCount").asInt()).isEqualTo(1);
        assertThat(eng.path("totalEmployeeCount").asInt()).isEqualTo(3);

        as(patch("/api/departments/ENG/disable"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HAS_EMPLOYEES"));

        for (String[] a : new String[][]{{"PLATFORM", "mia"}, {"PLATFORM", "leo"}, {"ENG", "ava"}}) {
            as(delete("/api/departments/" + a[0] + "/employees/" + a[1])).andExpect(status().isOk());
        }
        as(patch("/api/departments/ENG/disable")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(3));

        assertThat(names(data(as(get("/api/departments/HQ/tree"))).path("children"))).containsExactly("Sales");
        assertThat(names(data(as(get("/api/departments/HQ/tree").param("includeDisabled", "true"))).path("children")))
                .containsExactly("Engineering", "Sales");

        send(post("/api/departments"), Map.of("parentId", "ENG", "code", "QA", "name", "QA"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARENT_DISABLED"));
    }

    @Test
    void hierarchyAndEmployeeTreesReflectAssignments() throws Exception {
        assign("SALES", "mia");
        JsonNode hierarchy = data(as(get("/api/departments/SALES/hierarchy")));
        assertThat(hierarchy.path("parent").path("code").asString()).isEqualTo("HQ");
        assertThat(codes(hierarchy.path("children"))).containsExactly("APAC");
        assertThat(hierarchy.path("employees").get(0).asString()).isEqualTo("mia");

        // 本人沒有 DEPT 權限也能查自己所屬的部門樹
        JsonNode trees = data(mvc.perform(get("/api/departments/employees/mia/trees")
                        .header(RootlineHeaders.TENANT_ID, tenant).header(RootlineHeaders.USER_ID, "mia")
                        .header(RootlineHeaders.USER_PERMISSIONS, ""))
                .andExpect(status().isOk()));
        assertThat(trees.get(0).path("code").asString()).isEqualTo("SALES");
        assertThat(codes(trees.get(0).path("children"))).containsExactly("APAC");
    }

    @Test
    void rebuildingFromEventsReproducesTheExactProjection() throws Exception {
        send(post("/api/departments/move"), Map.of("departmentId", "ENG", "newParentId", "SALES"));
        send(patch("/api/departments/MOBILE/name"), Map.of("name", "Mobile Apps"));
        send(patch("/api/departments/APAC/sort-order"), Map.of("sortOrder", 5));
        assign("PLATFORM", "mia");
        as(patch("/api/departments/MOBILE/disable"));
        Map<String, List<Map<String, Object>>> before = projectionSnapshot();

        // 故意弄壞投影，再從事件庫重建
        jdbc.update("DELETE FROM department_tree WHERE tenant_id = ?", tenant);
        jdbc.update("UPDATE department_view SET name = 'corrupted' WHERE tenant_id = ?", tenant);

        JsonNode result = data(as(post("/api/departments/projections/rebuild")).andExpect(status().isOk()));
        assertThat(result.path("departments").asInt()).isEqualTo(6);
        assertThat(result.path("eventsReplayed").asLong()).isEqualTo(11);

        assertThat(projectionSnapshot()).isEqualTo(before);
    }

    @Test
    void tenantsCannotSeeEachOther() throws Exception {
        mvc.perform(get("/api/departments/HQ/tree")
                        .header(RootlineHeaders.TENANT_ID, "other-tenant").header(RootlineHeaders.USER_ID, "admin")
                        .header(RootlineHeaders.USER_PERMISSIONS, "*:ADMIN_ALL"))
                .andExpect(status().isNotFound());
    }

    @Test
    void writeRequiresTreeWritePermission() throws Exception {
        mvc.perform(post("/api/departments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"X\"}")
                        .header(RootlineHeaders.TENANT_ID, tenant).header(RootlineHeaders.USER_ID, "viewer")
                        .header(RootlineHeaders.USER_PERMISSIONS, "DEPT:TREE_READ"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleVersionIsRejectedByTheEventStore() {
        TenantId tenantId = TenantId.of(tenant);
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                store.append(tenantId, DepartmentId.of("ENG"), 0,
                        List.of(new dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentRenamed(
                                UUID.randomUUID(), java.time.Instant.now(), tenant, "ENG", "admin", "Engineering", "X")))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    /**
     * 經典併發成環：ENG 搬到 SALES 底下、SALES 同時搬到 ENG 底下。
     * 租戶鎖讓兩者排隊，後執行的那個看得到前者的結果而被擋下。
     */
    @Test
    void concurrentCrossMovesCannotCreateACycle() throws Exception {
        TenantId tenantId = TenantId.of(tenant);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (String[] move : new String[][]{{"ENG", "SALES"}, {"SALES", "ENG"}}) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    commands.move(tenantId, move[0], move[1], "admin");
                    return "ok";
                } catch (DomainException e) {
                    return e.code();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("ok", "CYCLE_DETECTED");
        Integer cycles = jdbc.queryForObject("""
                SELECT count(*) FROM department_tree a JOIN department_tree b
                  ON a.tenant_id = b.tenant_id AND a.ancestor_id = b.descendant_id AND a.descendant_id = b.ancestor_id
                WHERE a.tenant_id = ? AND a.ancestor_id <> a.descendant_id
                """, Integer.class, tenant);
        assertThat(cycles).isZero();
    }

    // ---------- helpers ----------

    private static Map<String, Object> node(String code, String name, Map<?, ?>... children) {
        return Map.of("id", code, "code", code, "name", name, "children", List.of(children));
    }

    private void assign(String department, String employee) throws Exception {
        send(post("/api/departments/" + department + "/employees"), Map.of("employeeId", employee))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header(RootlineHeaders.TENANT_ID, tenant)
                .header(RootlineHeaders.USER_ID, "admin")
                .header(RootlineHeaders.USER_PERMISSIONS, "DEPT:ADMIN_ALL");
    }

    private ResultActions as(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(admin(request));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(admin(request).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    private static List<String> names(JsonNode nodes) {
        List<String> names = new ArrayList<>();
        nodes.forEach(n -> names.add(n.path("name").asString()));
        return names;
    }

    private static List<String> codes(JsonNode nodes) {
        List<String> codes = new ArrayList<>();
        nodes.forEach(n -> codes.add(n.path("code").asString()));
        return codes;
    }

    private int closureRows() {
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM department_tree WHERE tenant_id = ?", Integer.class, tenant);
        return rows == null ? 0 : rows;
    }

    private Map<String, List<Map<String, Object>>> projectionSnapshot() {
        return Map.of(
                "view", jdbc.queryForList("SELECT * FROM department_view WHERE tenant_id = ? ORDER BY id", tenant),
                "tree", jdbc.queryForList("""
                        SELECT ancestor_id, descendant_id, depth FROM department_tree WHERE tenant_id = ?
                        ORDER BY ancestor_id, descendant_id
                        """, tenant),
                "employees", jdbc.queryForList(
                        "SELECT department_id, employee_id FROM department_employees WHERE tenant_id = ? ORDER BY 1, 2", tenant));
    }
}
