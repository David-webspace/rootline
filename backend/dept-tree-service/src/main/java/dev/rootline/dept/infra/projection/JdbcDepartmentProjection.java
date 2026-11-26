package dev.rootline.dept.infra.projection;

import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentCreated;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentDeleted;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentDisabled;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentMerged;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentMoved;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentRenamed;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentRestored;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentSortOrderChanged;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.EmployeeAssigned;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.EmployeeUnassigned;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentProjection;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 把事件投影到 department_view、department_tree（閉包表）、department_employees。
 * <p>
 * 閉包表的搬移用經典的兩段式 SQL：先切斷「子樹」與「舊祖先」之間的所有列，
 * 再把「新上層的所有祖先」與「子樹的所有節點」做笛卡兒積接回去。整棵子樹一次搬完，不需遞迴。
 */
@Component
class JdbcDepartmentProjection implements DepartmentProjection {

    private final JdbcTemplate jdbc;

    JdbcDepartmentProjection(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void apply(DepartmentEvent event) {
        String t = event.tenantId();
        String id = event.departmentId();
        switch (event) {
            case DepartmentCreated e -> {
                jdbc.update("""
                        INSERT INTO department_view
                            (tenant_id, id, parent_id, code, name, status, sort_order, version, updated_at, updated_by)
                        VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0, ?, ?)
                        """, t, id, e.parentId(), e.code(), e.name(), e.sortOrder(),
                        Timestamp.from(e.occurredAt()), e.operator());
                jdbc.update("INSERT INTO department_tree VALUES (?, ?, ?, 0)", t, id, id);
                if (e.parentId() != null) {
                    jdbc.update("""
                            INSERT INTO department_tree (tenant_id, ancestor_id, descendant_id, depth)
                            SELECT tenant_id, ancestor_id, ?, depth + 1 FROM department_tree
                            WHERE tenant_id = ? AND descendant_id = ?
                            """, id, t, e.parentId());
                }
            }
            case DepartmentMoved e -> {
                reparent(t, id, e.newParentId());
                jdbc.update("UPDATE department_view SET parent_id = ? WHERE tenant_id = ? AND id = ?",
                        e.newParentId(), t, id);
            }
            case DepartmentRenamed e ->
                    jdbc.update("UPDATE department_view SET name = ? WHERE tenant_id = ? AND id = ?", e.newName(), t, id);
            case DepartmentSortOrderChanged e ->
                    jdbc.update("UPDATE department_view SET sort_order = ? WHERE tenant_id = ? AND id = ?",
                            e.sortOrder(), t, id);
            case DepartmentDisabled e ->
                    jdbc.update("UPDATE department_view SET status = 'DISABLED' WHERE tenant_id = ? AND id = ?", t, id);
            case EmployeeAssigned e -> {
                jdbc.update("INSERT INTO department_employees VALUES (?, ?, ?)", t, id, e.employeeId());
                jdbc.update("UPDATE department_view SET direct_headcount = direct_headcount + 1 WHERE tenant_id = ? AND id = ?",
                        t, id);
            }
            case EmployeeUnassigned e -> {
                jdbc.update("DELETE FROM department_employees WHERE tenant_id = ? AND department_id = ? AND employee_id = ?",
                        t, id, e.employeeId());
                jdbc.update("UPDATE department_view SET direct_headcount = direct_headcount - 1 WHERE tenant_id = ? AND id = ?",
                        t, id);
            }
            case DepartmentDeleted e ->
                    jdbc.update("UPDATE department_view SET deleted = TRUE WHERE tenant_id = ? AND id = ?", t, id);
            case DepartmentRestored e -> {
                if (e.reattachedToRoot()) {
                    reparent(t, id, null);
                    jdbc.update("UPDATE department_view SET parent_id = NULL WHERE tenant_id = ? AND id = ?", t, id);
                }
                jdbc.update("UPDATE department_view SET deleted = FALSE WHERE tenant_id = ? AND id = ?", t, id);
            }
            case DepartmentMerged e -> jdbc.update(
                    "UPDATE department_view SET status = 'DISABLED', merged_into = ? WHERE tenant_id = ? AND id = ?",
                    e.targetDepartmentId(), t, id);
        }
        jdbc.update("UPDATE department_view SET version = version + 1, updated_at = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                Timestamp.from(event.occurredAt()), event.operator(), t, id);
    }

    /** 把以 {@code node} 為根的整棵子樹改掛到 {@code newParent} 底下（null 表示變成 root）。 */
    private void reparent(String tenant, String node, String newParent) {
        jdbc.update("""
                DELETE FROM department_tree
                WHERE tenant_id = ?
                  AND descendant_id IN (SELECT descendant_id FROM department_tree WHERE tenant_id = ? AND ancestor_id = ?)
                  AND ancestor_id IN (SELECT ancestor_id FROM department_tree
                                      WHERE tenant_id = ? AND descendant_id = ? AND ancestor_id <> ?)
                """, tenant, tenant, node, tenant, node, node);
        if (newParent != null) {
            jdbc.update("""
                    INSERT INTO department_tree (tenant_id, ancestor_id, descendant_id, depth)
                    SELECT ?, sup.ancestor_id, sub.descendant_id, sup.depth + sub.depth + 1
                    FROM department_tree sup
                    JOIN department_tree sub ON sub.tenant_id = sup.tenant_id
                    WHERE sup.tenant_id = ? AND sup.descendant_id = ? AND sub.ancestor_id = ?
                    """, tenant, tenant, newParent, node);
        }
    }

    @Override
    public void clear(TenantId tenantId) {
        jdbc.update("DELETE FROM department_employees WHERE tenant_id = ?", tenantId.value());
        jdbc.update("DELETE FROM department_tree WHERE tenant_id = ?", tenantId.value());
        jdbc.update("DELETE FROM department_view WHERE tenant_id = ?", tenantId.value());
    }
}
