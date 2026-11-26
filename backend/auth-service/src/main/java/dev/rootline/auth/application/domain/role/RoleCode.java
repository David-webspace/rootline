package dev.rootline.auth.application.domain.role;

import dev.rootline.auth.application.domain.shared.BusinessCode;

/** 角色代碼，租戶內唯一，建立後不可變，例如 {@code TENANT_ADMIN}。 */
public record RoleCode(String value) {

    public RoleCode {
        value = BusinessCode.normalize(value, "ROLE_CODE_INVALID", "Role code");
    }

    public static RoleCode of(String value) {
        return new RoleCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
