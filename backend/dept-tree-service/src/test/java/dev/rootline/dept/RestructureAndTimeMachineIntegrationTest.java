package dev.rootline.dept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.rootline.kernel.security.RootlineHeaders;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 組織重整（刪除、復原、合併）與時光機。樹的形狀同 {@link DepartmentTreeIntegrationTest}：
 * HQ → {ENG → {PLATFORM, MOBILE}, SALES → {APAC}}。測試設定的快照間隔為 3。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RestructureAndTimeMachineIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;

    String tenant;

    @BeforeEach
    void buildTree() throws Exception {
        tenant = "t" + UUID.randomUUID().toString().substring(0, 8);
        send(post("/api/departments/tree"), Map.of("root", node("HQ", "Northwind Labs",
                node("ENG", "Engineering", node("PLATFORM", "Platform"), node("MOBILE", "Mobile")),
                node("SALES", "Sales", node("APAC", "APAC"))))).andExpect(status().isCreated());
    }

    @Test
    void deleteCascadesAndRestoreBringsTheWholeSubtreeBack() throws Exception {
        as(delete("/api/departments/ENG")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(3));
        assertThat(names(data(as(get("/api/departments/HQ/tree"))).path("children"))).containsExactly("Sales");
        as(get("/api/departments/PLATFORM/tree")).andExpect(status().isNotFound());

        JsonNode restored = data(as(post("/api/departments/ENG/restore")).andExpect(status().isOk()));
        assertThat(restored.path("restored").asInt()).isEqualTo(3);
        assertThat(restored.path("reattachedToRoot").asBoolean()).isFalse();
        assertThat(names(data(as(get("/api/departments/ENG/tree"))).path("children")))
                .containsExactly("Mobile", "Platform");
    }

    @Test
    void deletedDepartmentsAreVisibleOnlyWhenRequested() throws Exception {
        as(delete("/api/departments/ENG"));

        JsonNode tree = data(as(get("/api/departments/HQ/tree").param("includeDeleted", "true")));
        assertThat(names(tree.path("children"))).containsExactly("Engineering", "Sales");
        JsonNode eng = tree.path("children").get(0);
        assertThat(eng.path("deleted").asBoolean()).isTrue();
        assertThat(names(eng.path("children"))).containsExactly("Mobile", "Platform");
        assertThat(tree.path("children").get(1).path("deleted").asBoolean()).isFalse();

        // 已刪除的部門本身也能當樹根查（前端時光機要看它的子樹再決定是否復原）
        as(get("/api/departments/ENG/tree").param("includeDeleted", "true")).andExpect(status().isOk());
        as(get("/api/departments/ENG/tree")).andExpect(status().isNotFound());
    }

    @Test
    void restoringAnOrphanReattachesItToTheTop() throws Exception {
        as(delete("/api/departments/PLATFORM"));
        as(delete("/api/departments/ENG"));

        JsonNode restored = data(as(post("/api/departments/PLATFORM/restore")));
        assertThat(restored.path("reattachedToRoot").asBoolean()).isTrue();
        assertThat(codes(data(as(get("/api/departments/PLATFORM/breadcrumbs"))))).containsExactly("PLATFORM");
        assertThat(codes(data(as(get("/api/departments/roots"))).path("content")))
                .containsExactlyInAnyOrder("HQ", "PLATFORM");
    }

    @Test
    void deletionRules() throws Exception {
        as(delete("/api/departments/HQ"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROOT_NOT_DELETABLE"));

        send(post("/api/departments/MOBILE/employees"), Map.of("employeeId", "mia"));
        as(delete("/api/departments/ENG"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_HAS_EMPLOYEES"));
        // 整批不生效：PLATFORM 不能因為排在前面就先被刪掉
        as(get("/api/departments/PLATFORM/tree")).andExpect(status().isOk());

        as(post("/api/departments/SALES/restore"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_DELETED"));
    }

    @Test
    void mergeMovesChildrenAndPeopleThenRetiresTheSource() throws Exception {
        send(post("/api/departments/ENG/employees"), Map.of("employeeId", "mia"));
        send(post("/api/departments/ENG/employees"), Map.of("employeeId", "leo"));
        send(post("/api/departments/SALES/employees"), Map.of("employeeId", "leo")); // 兩邊都有的人不會重複

        JsonNode result = data(send(post("/api/departments/ENG/merge"), Map.of("targetDeptId", "SALES"))
                .andExpect(status().isOk()));
        assertThat(result.path("movedChildren").asInt()).isEqualTo(2);
        assertThat(result.path("movedEmployees").asInt()).isEqualTo(2);

        JsonNode sales = data(as(get("/api/departments/SALES/hierarchy")));
        assertThat(codes(sales.path("children"))).containsExactlyInAnyOrder("APAC", "MOBILE", "PLATFORM");
        assertThat(sales.path("employees").toString()).isEqualTo("[\"leo\",\"mia\"]");

        JsonNode eng = data(as(get("/api/departments/ENG/state/current")));
        assertThat(eng.path("status").asString()).isEqualTo("DISABLED");
        assertThat(eng.path("employees").isEmpty()).isTrue();
        assertThat(String.valueOf(jdbc.queryForObject(
                "SELECT merged_into FROM department_view WHERE tenant_id = ? AND id = 'ENG'", String.class, tenant)))
                .isEqualTo("SALES");
    }

    @Test
    void cannotMergeIntoADescendant() throws Exception {
        send(post("/api/departments/ENG/merge"), Map.of("targetDeptId", "PLATFORM"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MERGE_INTO_DESCENDANT"));
        send(post("/api/departments/ENG/merge"), Map.of("targetDeptId", "ENG"))
                .andExpect(jsonPath("$.code").value("MERGE_INTO_DESCENDANT"));
    }

    @Test
    void timeMachineReconstructsPastStatesIncludingAfterDeletion() throws Exception {
        Instant beforeRename = pause();
        send(patch("/api/departments/MOBILE/name"), Map.of("name", "Mobile Apps"));
        send(post("/api/departments/MOBILE/employees"), Map.of("employeeId", "mia"));
        Instant afterAssign = pause();
        send(post("/api/departments/move"), Map.of("departmentId", "MOBILE", "newParentId", "SALES"));
        as(delete("/api/departments/MOBILE/employees/mia"));
        as(delete("/api/departments/MOBILE"));

        JsonNode past = data(as(get("/api/departments/MOBILE/state/at").param("timestamp", beforeRename.toString())));
        assertThat(past.path("name").asString()).isEqualTo("Mobile");
        assertThat(past.path("parentId").asString()).isEqualTo("ENG");

        JsonNode middle = data(as(get("/api/departments/MOBILE/state/at").param("timestamp", afterAssign.toString())));
        assertThat(middle.path("name").asString()).isEqualTo("Mobile Apps");
        assertThat(middle.path("employees").toString()).isEqualTo("[\"mia\"]");
        assertThat(middle.path("version").asInt()).isEqualTo(3);

        JsonNode now = data(as(get("/api/departments/MOBILE/state/current")));
        assertThat(now.path("deleted").asBoolean()).isTrue();
        assertThat(now.path("parentId").asString()).isEqualTo("SALES");
        assertThat(now.path("lastModifiedBy").asString()).isEqualTo("admin");

        List<String> types = new ArrayList<>();
        data(as(get("/api/departments/MOBILE/events"))).forEach(e -> types.add(e.path("eventType").asString()));
        assertThat(types).containsExactly("DepartmentCreated", "DepartmentRenamed", "EmployeeAssigned",
                "DepartmentMoved", "EmployeeUnassigned", "DepartmentDeleted");

        as(get("/api/departments/MOBILE/state/at").param("timestamp", "2000-01-01T00:00:00Z"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_YET_EXISTED"));
    }

    @Test
    void snapshotsAreTakenAndLoadingFromThemMatchesFullReplay() throws Exception {
        for (int i = 1; i <= 7; i++) {
            send(patch("/api/departments/APAC/sort-order"), Map.of("sortOrder", i));
        }
        // 1 個建立 + 7 次排序 = 8 個事件；間隔 3 → 版本 3、6 各一份快照
        List<Long> versions = jdbc.queryForList(
                "SELECT version FROM department_snapshots WHERE tenant_id = ? AND aggregate_id = 'APAC' ORDER BY version",
                Long.class, tenant);
        assertThat(versions).containsExactly(3L, 6L);

        // 透過快照載入後再寫一筆，版本必須正確接續（否則會撞樂觀鎖）
        send(patch("/api/departments/APAC/name"), Map.of("name", "Asia Pacific")).andExpect(status().isOk());
        JsonNode state = data(as(get("/api/departments/APAC/state/current")));
        assertThat(state.path("version").asInt()).isEqualTo(9);
        assertThat(state.path("sortOrder").asInt()).isEqualTo(7);

        // 刪掉所有快照，結果必須完全一樣：快照只是加速，不是真相
        jdbc.update("DELETE FROM department_snapshots WHERE tenant_id = ?", tenant);
        JsonNode replayed = data(as(get("/api/departments/APAC/state/current")));
        assertThat(replayed.path("version").asInt()).isEqualTo(9);
        assertThat(replayed.path("name").asString()).isEqualTo("Asia Pacific");
    }

    // ---------- helpers ----------

    /** 拉開事件時間，讓「某時間點之前」的邊界明確。 */
    private static Instant pause() throws InterruptedException {
        Thread.sleep(30);
        Instant instant = Instant.now();
        Thread.sleep(30);
        return instant;
    }

    private static Map<String, Object> node(String code, String name, Map<?, ?>... children) {
        return Map.of("id", code, "code", code, "name", name, "children", List.of(children));
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
}
