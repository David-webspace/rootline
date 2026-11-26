package dev.rootline.auth.infra.messaging;

import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.DepartmentDirectory;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcDepartmentDirectory implements DepartmentDirectory {

    private final JdbcTemplate jdbc;

    JdbcDepartmentDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsertDepartment(TenantId tenantId, String departmentId, String name) {
        jdbc.update("""
                INSERT INTO dept_directory (tenant_id, department_id, name) VALUES (?, ?, ?)
                ON CONFLICT (tenant_id, department_id) DO UPDATE SET name = EXCLUDED.name
                """, tenantId.value(), departmentId, name);
    }

    @Override
    public void markDeleted(TenantId tenantId, String departmentId, boolean deleted) {
        jdbc.update("UPDATE dept_directory SET deleted = ? WHERE tenant_id = ? AND department_id = ?",
                deleted, tenantId.value(), departmentId);
    }

    @Override
    public void assign(TenantId tenantId, String username, String departmentId) {
        jdbc.update("INSERT INTO user_departments VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                tenantId.value(), username, departmentId);
    }

    @Override
    public void unassign(TenantId tenantId, String username, String departmentId) {
        jdbc.update("DELETE FROM user_departments WHERE tenant_id = ? AND username = ? AND department_id = ?",
                tenantId.value(), username, departmentId);
    }

    /** 部門名稱還沒同步到（事件尚未到達）時，退而顯示部門 id。 */
    @Override
    public List<DepartmentRef> departmentsOf(TenantId tenantId, Username username) {
        return jdbc.query("""
                SELECT u.department_id, COALESCE(d.name, u.department_id) AS name
                FROM user_departments u
                LEFT JOIN dept_directory d ON d.tenant_id = u.tenant_id AND d.department_id = u.department_id
                WHERE u.tenant_id = ? AND u.username = ? AND COALESCE(d.deleted, FALSE) = FALSE
                ORDER BY name
                """, (rs, i) -> new DepartmentRef(rs.getString(1), rs.getString(2)), tenantId.value(), username.value());
    }
}
