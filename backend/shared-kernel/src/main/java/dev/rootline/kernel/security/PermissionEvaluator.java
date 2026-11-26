package dev.rootline.kernel.security;

import java.util.Set;

/**
 * 權限字串格式為 {@code SYSTEM:PERMISSION}，例如 {@code IAM:USER_READ}。
 * <ul>
 *   <li>{@code *:ADMIN_ALL}：全域最高權限</li>
 *   <li>{@code SYSTEM:ADMIN_ALL}：該系統內的所有權限</li>
 * </ul>
 */
public final class PermissionEvaluator {

    public static final String ADMIN_ALL = "ADMIN_ALL";
    public static final String SUPER_WILDCARD = "*:" + ADMIN_ALL;

    private PermissionEvaluator() {
    }

    public static boolean isGranted(Set<String> granted, String required) {
        if (required == null || required.isBlank()) {
            return true;
        }
        if (granted == null || granted.isEmpty()) {
            return false;
        }
        if (granted.contains(SUPER_WILDCARD) || granted.contains(required)) {
            return true;
        }
        int separator = required.indexOf(':');
        if (separator <= 0) {
            return false;
        }
        String system = required.substring(0, separator);
        return granted.contains(system + ":" + ADMIN_ALL);
    }
}
