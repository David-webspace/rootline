package dev.rootline.dept.iface.rest;

import dev.rootline.dept.application.service.DepartmentCommandService.TreeSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** 請求 DTO：只檢查必填，格式規則交給 domain value object。 */
final class Requests {

    private Requests() {
    }

    record CreateDepartment(String id, String parentId, @NotBlank String code, @NotBlank String name) {
    }

    record TreeNode(String id, @NotBlank String code, @NotBlank String name, List<@Valid TreeNode> children) {

        TreeSpec toSpec() {
            return new TreeSpec(id, code, name,
                    children == null ? List.of() : children.stream().map(TreeNode::toSpec).toList());
        }

        int size() {
            return 1 + (children == null ? 0 : children.stream().mapToInt(TreeNode::size).sum());
        }
    }

    record CreateTree(String parentId, @NotNull @Valid TreeNode root) {
    }

    record Move(@NotBlank String departmentId, String newParentId) {
    }

    record Rename(@NotBlank String name) {
    }

    record SortOrder(@NotNull @PositiveOrZero Integer sortOrder) {
    }

    record AssignEmployee(@NotBlank String employeeId) {
    }

    record Merge(@NotBlank String targetDeptId) {
    }
}
