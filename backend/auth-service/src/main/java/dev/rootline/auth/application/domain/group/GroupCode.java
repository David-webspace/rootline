package dev.rootline.auth.application.domain.group;

import dev.rootline.auth.application.domain.shared.BusinessCode;

/** 群組代碼，租戶內唯一，建立後不可變，例如 {@code FINANCE_TEAM}。 */
public record GroupCode(String value) {

    public GroupCode {
        value = BusinessCode.normalize(value, "GROUP_CODE_INVALID", "Group code");
    }

    public static GroupCode of(String value) {
        return new GroupCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
