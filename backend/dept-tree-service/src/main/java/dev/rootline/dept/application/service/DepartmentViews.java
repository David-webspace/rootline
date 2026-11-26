package dev.rootline.dept.application.service;

import dev.rootline.dept.application.port.DepartmentReadModel.Node;
import java.util.List;

/** 查詢端回傳的資料結構。 */
public final class DepartmentViews {

    private DepartmentViews() {
    }

    /** {@code deleted} 只有在查詢明確要求包含已刪除部門時才可能為 true（給時光機復活用）。 */
    public record FlatNode(String id, String parentId, String code, String name, String status, int sortOrder,
                           boolean deleted, int depth, int directEmployeeCount, int totalEmployeeCount) {

        static FlatNode of(Node node) {
            return new FlatNode(node.id(), node.parentId(), node.code(), node.name(), node.status(), node.sortOrder(),
                    node.deleted(), node.depth(), node.directHeadcount(), node.totalHeadcount());
        }
    }

    public record TreeNode(String id, String parentId, String code, String name, String status, int sortOrder,
                           boolean deleted, int depth, int directEmployeeCount, int totalEmployeeCount, List<TreeNode> children) {
    }

    public record Hierarchy(FlatNode current, FlatNode parent, List<FlatNode> children, List<String> employees) {
    }

    public record RootsPage(List<FlatNode> content, long totalElements, int page, int size) {
    }

    public record RebuildResult(long eventsReplayed, int departments, int closureRows) {
    }
}
