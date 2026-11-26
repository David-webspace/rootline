package dev.rootline.dept.infra.projection;

import dev.rootline.dept.application.domain.dept.DepartmentCode;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.dept.EmployeeId;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentReadModel;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
class JdbcDepartmentReadModel implements DepartmentReadModel {

    /** 子樹人數總和：每個節點加總自己子樹內（未刪除）部門的直屬人數，一樣靠閉包表一次算完。 */
    private static final String TOTAL_HEADCOUNT = """
            (SELECT COALESCE(SUM(v2.direct_headcount), 0) FROM department_tree t2
             JOIN department_view v2 ON v2.tenant_id = t2.tenant_id AND v2.id = t2.descendant_id
             WHERE t2.tenant_id = v.tenant_id AND t2.ancestor_id = v.id AND NOT v2.deleted) AS total_headcount
            """;

    private static final RowMapper<Node> NODE = (rs, i) -> new Node(
            rs.getString("id"), rs.getString("parent_id"), rs.getString("code"), rs.getString("name"),
            rs.getString("status"), rs.getInt("sort_order"), rs.getBoolean("deleted"), rs.getInt("depth"),
            rs.getInt("direct_headcount"), rs.getInt("total_headcount"));

    private final JdbcTemplate jdbc;

    JdbcDepartmentReadModel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Node> find(TenantId tenantId, DepartmentId id) {
        return jdbc.query("SELECT v.*, 0 AS depth, " + TOTAL_HEADCOUNT
                        + " FROM department_view v WHERE v.tenant_id = ? AND v.id = ?",
                NODE, tenantId.value(), id.value()).stream().findFirst();
    }

    @Override
    public boolean codeExists(TenantId tenantId, DepartmentCode code) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM department_view WHERE tenant_id = ? AND code = ?)",
                Boolean.class, tenantId.value(), code.value());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean isAncestorOrSelf(TenantId tenantId, DepartmentId ancestor, DepartmentId node) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM department_tree
                               WHERE tenant_id = ? AND ancestor_id = ? AND descendant_id = ?)
                """, Boolean.class, tenantId.value(), ancestor.value(), node.value());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public List<Node> subtree(TenantId tenantId, DepartmentId root, boolean includeDeleted, boolean includeDisabled) {
        return jdbc.query("SELECT v.*, t.depth, " + TOTAL_HEADCOUNT + """
                         FROM department_tree t
                         JOIN department_view v ON v.tenant_id = t.tenant_id AND v.id = t.descendant_id
                         WHERE t.tenant_id = ? AND t.ancestor_id = ?
                           AND (? OR NOT v.deleted)
                           AND (? OR v.status = 'ACTIVE')
                         ORDER BY t.depth, v.sort_order, v.name
                        """,
                NODE, tenantId.value(), root.value(), includeDeleted, includeDisabled);
    }

    @Override
    public List<Node> ancestors(TenantId tenantId, DepartmentId id) {
        return jdbc.query("SELECT v.*, t.depth, " + TOTAL_HEADCOUNT + """
                         FROM department_tree t
                         JOIN department_view v ON v.tenant_id = t.tenant_id AND v.id = t.ancestor_id
                         WHERE t.tenant_id = ? AND t.descendant_id = ?
                         ORDER BY t.depth DESC
                        """,
                NODE, tenantId.value(), id.value());
    }

    @Override
    public List<Node> children(TenantId tenantId, DepartmentId id) {
        return jdbc.query("SELECT v.*, 1 AS depth, " + TOTAL_HEADCOUNT + """
                         FROM department_view v
                         WHERE v.tenant_id = ? AND v.parent_id = ? AND NOT v.deleted
                         ORDER BY v.sort_order, v.name
                        """,
                NODE, tenantId.value(), id.value());
    }

    @Override
    public List<Node> search(TenantId tenantId, String keyword, int limit) {
        String pattern = "%" + escapeLike(keyword.trim()) + "%";
        return jdbc.query("SELECT v.*, 0 AS depth, " + TOTAL_HEADCOUNT + """
                         FROM department_view v
                         WHERE v.tenant_id = ? AND NOT v.deleted
                           AND (v.name ILIKE ? ESCAPE '\\' OR v.code ILIKE ? ESCAPE '\\')
                         ORDER BY v.name
                         LIMIT ?
                        """,
                NODE, tenantId.value(), pattern, pattern, limit);
    }

    @Override
    public Page<Node> roots(TenantId tenantId, String nameFilter, int page, int size) {
        String pattern = nameFilter == null || nameFilter.isBlank() ? "%" : "%" + escapeLike(nameFilter.trim()) + "%";
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM department_view
                WHERE tenant_id = ? AND parent_id IS NULL AND NOT deleted AND name ILIKE ? ESCAPE '\\'
                """, Long.class, tenantId.value(), pattern);
        List<Node> content = jdbc.query("SELECT v.*, 0 AS depth, " + TOTAL_HEADCOUNT + """
                         FROM department_view v
                         WHERE v.tenant_id = ? AND v.parent_id IS NULL AND NOT v.deleted AND v.name ILIKE ? ESCAPE '\\'
                         ORDER BY v.sort_order, v.name
                         LIMIT ? OFFSET ?
                        """,
                NODE, tenantId.value(), pattern, size, (long) page * size);
        return new Page<>(content, total == null ? 0 : total, page, size);
    }

    @Override
    public List<String> employees(TenantId tenantId, DepartmentId id) {
        return jdbc.queryForList("""
                SELECT employee_id FROM department_employees WHERE tenant_id = ? AND department_id = ? ORDER BY employee_id
                """, String.class, tenantId.value(), id.value());
    }

    @Override
    public List<DepartmentId> departmentsOf(TenantId tenantId, EmployeeId employee) {
        return jdbc.queryForList("""
                SELECT e.department_id FROM department_employees e
                JOIN department_view v ON v.tenant_id = e.tenant_id AND v.id = e.department_id
                WHERE e.tenant_id = ? AND e.employee_id = ? AND NOT v.deleted
                ORDER BY e.department_id
                """, String.class, tenantId.value(), employee.value()).stream().map(DepartmentId::of).toList();
    }

    @Override
    public Statistics statistics(TenantId tenantId) {
        Integer departments = jdbc.queryForObject("SELECT count(*) FROM department_view WHERE tenant_id = ?",
                Integer.class, tenantId.value());
        Integer closure = jdbc.queryForObject("SELECT count(*) FROM department_tree WHERE tenant_id = ?",
                Integer.class, tenantId.value());
        return new Statistics(departments == null ? 0 : departments, closure == null ? 0 : closure);
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
