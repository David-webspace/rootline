package dev.rootline.auth.iface.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 請求 DTO。這裡只做「欄位有沒有填、長度合不合理」的粗檢查，
 * 格式規則（帳號字元、email、密碼政策）由 domain value object 負責，避免兩處規則不一致。
 */
final class Requests {

    private Requests() {
    }

    record Login(@NotBlank String tenantCode, @NotBlank String username, @NotBlank @Size(max = 200) String password) {
    }

    record Register(@NotBlank String tenantCode, @NotBlank String username, @NotBlank String email,
                    @NotBlank @Size(max = 200) String password) {
    }

    record CreateUser(@NotBlank String username, @NotBlank String email, @NotBlank @Size(max = 200) String password) {
    }

    record ChangePassword(@Size(max = 200) String currentPassword, @NotBlank @Size(max = 200) String newPassword) {
    }

    record UpdateProfile(@NotBlank String email) {
    }

    record CreateRole(@NotBlank String roleCode, @NotBlank String roleName) {
    }

    record CreateGroup(@NotBlank String groupCode, @NotBlank String groupName) {
    }

    record Rename(@NotBlank String name) {
    }

    record GrantPermission(@NotBlank String systemCode, @NotBlank String permissionCode) {
    }
}
