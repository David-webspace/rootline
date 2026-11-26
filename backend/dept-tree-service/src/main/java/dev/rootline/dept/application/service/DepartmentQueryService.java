package dev.rootline.dept.application.service;

import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.dept.EmployeeId;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentReadModel;
import dev.rootline.dept.application.port.DepartmentReadModel.Node;
import dev.rootline.dept.application.port.DepartmentReadModel.Page;
import dev.rootline.dept.application.service.DepartmentViews.FlatNode;
import dev.rootline.dept.application.service.DepartmentViews.Hierarchy;
import dev.rootline.dept.application.service.DepartmentViews.RootsPage;
import dev.rootline.dept.application.service.DepartmentViews.TreeNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 讀取端查詢：全部走投影表，不碰事件庫。 */
@Service
@Transactional(readOnly = true)
public class DepartmentQueryService {

    static final int MAX_PAGE_SIZE = 100;
    static final int SEARCH_LIMIT = 50;

    private final DepartmentReadModel readModel;

    public DepartmentQueryService(DepartmentReadModel readModel) {
        this.readModel = readModel;
    }

    /**
     * 以 {@code rootId} 為根的巢狀樹；同層依 sortOrder、名稱排序。
     * {@code includeDeleted} 時已刪除的部門（含根本身）也會出現並標記，讓前端可以從樹上直接復原。
     */
    public TreeNode tree(TenantId tenantId, String rootId, boolean includeDisabled, boolean includeDeleted) {
        DepartmentId root = DepartmentId.of(rootId);
        if (includeDeleted) {
            readModel.find(tenantId, root).orElseThrow(
                    () -> DomainException.notFound("DEPARTMENT_NOT_FOUND", "Department " + root + " not found"));
        } else {
            requireVisible(tenantId, root);
        }
        List<Node> nodes = readModel.subtree(tenantId, root, includeDeleted, includeDisabled);
        if (nodes.isEmpty()) {
            throw DomainException.notFound("DEPARTMENT_NOT_FOUND", "Department " + root + " not found");
        }
        return assemble(root.value(), nodes);
    }

    public List<FlatNode> breadcrumbs(TenantId tenantId, String departmentId) {
        DepartmentId id = DepartmentId.of(departmentId);
        requireVisible(tenantId, id);
        return readModel.ancestors(tenantId, id).stream().map(FlatNode::of).toList();
    }

    public List<FlatNode> search(TenantId tenantId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return readModel.search(tenantId, keyword, SEARCH_LIMIT).stream().map(FlatNode::of).toList();
    }

    public RootsPage roots(TenantId tenantId, String nameFilter, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Node> result = readModel.roots(tenantId, nameFilter, safePage, safeSize);
        return new RootsPage(result.content().stream().map(FlatNode::of).toList(), result.totalElements(),
                safePage, safeSize);
    }

    public Hierarchy hierarchy(TenantId tenantId, String departmentId) {
        DepartmentId id = DepartmentId.of(departmentId);
        Node current = requireVisible(tenantId, id);
        FlatNode parent = current.parentId() == null ? null
                : readModel.find(tenantId, DepartmentId.of(current.parentId())).map(FlatNode::of).orElse(null);
        return new Hierarchy(FlatNode.of(current), parent,
                readModel.children(tenantId, id).stream().map(FlatNode::of).toList(),
                readModel.employees(tenantId, id));
    }

    /** 員工所屬的每個部門各自展開成一棵子樹。 */
    public List<TreeNode> employeeTrees(TenantId tenantId, String employeeId) {
        EmployeeId employee = EmployeeId.of(employeeId);
        return readModel.departmentsOf(tenantId, employee).stream()
                .map(id -> assemble(id.value(), readModel.subtree(tenantId, id, false, true)))
                .toList();
    }

    private Node requireVisible(TenantId tenantId, DepartmentId id) {
        return readModel.find(tenantId, id)
                .filter(n -> !n.deleted())
                .orElseThrow(() -> DomainException.notFound("DEPARTMENT_NOT_FOUND", "Department " + id + " not found"));
    }

    /**
     * 把扁平的子樹列表組成巢狀結構。subtree 查詢已依 depth 排序，所以處理到某節點時其上層一定已建立；
     * 上層不在結果裡的節點（例如上層被停用而被過濾掉）會一併略過，不會變成孤兒。
     */
    private static TreeNode assemble(String rootId, List<Node> nodes) {
        Map<String, List<Node>> childrenByParent = new HashMap<>();
        Node root = null;
        for (Node node : nodes) {
            if (node.id().equals(rootId)) {
                root = node;
            } else if (node.parentId() != null) {
                childrenByParent.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
            }
        }
        if (root == null) {
            throw DomainException.notFound("DEPARTMENT_NOT_FOUND", "Department " + rootId + " not found");
        }
        return build(root, childrenByParent);
    }

    private static TreeNode build(Node node, Map<String, List<Node>> childrenByParent) {
        List<TreeNode> children = childrenByParent.getOrDefault(node.id(), List.of()).stream()
                .sorted(Comparator.comparingInt(Node::sortOrder).thenComparing(Node::name))
                .map(child -> build(child, childrenByParent))
                .toList();
        return new TreeNode(node.id(), node.parentId(), node.code(), node.name(), node.status(), node.sortOrder(),
                node.deleted(), node.depth(), node.directHeadcount(), node.totalHeadcount(), children);
    }
}
