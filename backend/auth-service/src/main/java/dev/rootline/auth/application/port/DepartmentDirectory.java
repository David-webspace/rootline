package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import java.util.List;

/** 使用者所屬部門的本地投影（來源是 dept-tree-service）。 */
public interface DepartmentDirectory {

    void upsertDepartment(TenantId tenantId, String departmentId, String name);

    void markDeleted(TenantId tenantId, String departmentId, boolean deleted);

    void assign(TenantId tenantId, String username, String departmentId);

    void unassign(TenantId tenantId, String username, String departmentId);

    List<DepartmentRef> departmentsOf(TenantId tenantId, Username username);

    record DepartmentRef(String id, String name) {
    }
}
