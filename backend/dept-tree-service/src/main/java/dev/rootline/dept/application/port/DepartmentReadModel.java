package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.dept.DepartmentCode;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.dept.EmployeeId;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.util.List;
import java.util.Optional;

/** 讀取端查詢。閉包表讓子樹、祖先、成環檢查都是單一 SQL，不需要遞迴。 */
public interface DepartmentReadModel {

    Optional<Node> find(TenantId tenantId, DepartmentId id);

    boolean codeExists(TenantId tenantId, DepartmentCode code);

    /** {@code ancestor} 是否為 {@code node} 本身或其祖先。 */
    boolean isAncestorOrSelf(TenantId tenantId, DepartmentId ancestor, DepartmentId node);

    /** 子樹（含自己），由上而下排序。 */
    List<Node> subtree(TenantId tenantId, DepartmentId root, boolean includeDeleted, boolean includeDisabled);

    /** 從最上層到自己的路徑。 */
    List<Node> ancestors(TenantId tenantId, DepartmentId id);

    List<Node> children(TenantId tenantId, DepartmentId id);

    List<Node> search(TenantId tenantId, String keyword, int limit);

    Page<Node> roots(TenantId tenantId, String nameFilter, int page, int size);

    List<String> employees(TenantId tenantId, DepartmentId id);

    List<DepartmentId> departmentsOf(TenantId tenantId, EmployeeId employee);

    Statistics statistics(TenantId tenantId);

    record Statistics(int departments, int closureRows) {
    }

    /**
     * @param depth          相對於查詢起點的深度（subtree / ancestors 才有意義，其他查詢為 0）
     * @param totalHeadcount 子樹（含自己）未刪除部門的人數總和
     */
    record Node(String id, String parentId, String code, String name, String status, int sortOrder, boolean deleted,
                int depth, int directHeadcount, int totalHeadcount) {
    }

    record Page<T>(List<T> content, long totalElements, int page, int size) {
    }
}
