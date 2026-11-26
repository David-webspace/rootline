package dev.rootline.auth.application.shared;

import java.time.Instant;
import java.util.List;

/** 查詢端回傳給介面層的唯讀資料。只放字串與基本型別，不外漏 domain 物件。 */
public final class Views {

    private Views() {
    }

    public record UserView(String id, String username, String email, String status, List<String> roles) {
    }

    public record PermissionView(String systemCode, String permissionCode, String permissionName) {
    }

    public record RoleView(String id, String roleCode, String roleName, boolean systemRoot,
                           List<PermissionView> permissions) {
    }

    public record GroupView(String groupCode, String groupName, List<String> members, List<String> roles) {
    }

    public record GroupRoleView(String groupCode, String groupName, List<String> roleCodes) {
    }

    public record DepartmentView(String id, String name) {
    }

    /** {@code departments} 來自 dept-tree 的事件投影，為最終一致：剛指派的部門可能晚幾百毫秒才出現。 */
    public record PermissionContextView(String username, String email, String status, List<String> personalRoles,
                                        List<GroupRoleView> groupRoles, List<PermissionView> permissions,
                                        List<DepartmentView> departments) {
    }

    public record PermissionDictEntryView(String systemCode, String permissionCode, String permissionName,
                                          String description, long version) {
    }

    public record LoginResult(String token, Instant expiresAt, String tenantId, String username,
                              List<String> roles, List<String> permissions) {
    }
}
