package dev.rootline.auth.application.domain.role;

import dev.rootline.auth.application.domain.shared.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 權限的識別：系統代碼 + 權限代碼，對外字串為 {@code SYSTEM:PERMISSION}。
 * 系統代碼可以是 {@code *}，只用在 {@code *:ADMIN_ALL}。
 */
public record PermissionKey(String systemCode, String permissionCode) {

    // Pattern 必須宣告在 SUPER_ADMIN 之前：static 欄位依文字順序初始化，
    // 建構 SUPER_ADMIN 時會用到它們。
    private static final Pattern SYSTEM = Pattern.compile("^(\\*|[A-Z][A-Z0-9_]{1,31})$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public static final String ADMIN_ALL = "ADMIN_ALL";
    public static final PermissionKey SUPER_ADMIN = new PermissionKey("*", ADMIN_ALL);

    public PermissionKey {
        systemCode = systemCode == null ? null : systemCode.trim().toUpperCase(Locale.ROOT);
        permissionCode = permissionCode == null ? null : permissionCode.trim().toUpperCase(Locale.ROOT);
        if (systemCode == null || !SYSTEM.matcher(systemCode).matches()) {
            throw DomainException.invalid("PERMISSION_INVALID", "System code must be '*' or A-Z, 0-9, '_'");
        }
        if (permissionCode == null || !CODE.matcher(permissionCode).matches()) {
            throw DomainException.invalid("PERMISSION_INVALID", "Permission code must be A-Z, 0-9, '_'");
        }
        if ("*".equals(systemCode) && !ADMIN_ALL.equals(permissionCode)) {
            throw DomainException.invalid("PERMISSION_INVALID", "System '*' only allows ADMIN_ALL");
        }
    }

    public static PermissionKey parse(String authority) {
        if (authority == null || authority.indexOf(':') <= 0) {
            throw DomainException.invalid("PERMISSION_INVALID", "Permission must look like SYSTEM:PERMISSION");
        }
        int separator = authority.indexOf(':');
        return new PermissionKey(authority.substring(0, separator), authority.substring(separator + 1));
    }

    /** 萬用權限（{@code *:ADMIN_ALL} 或 {@code SYSTEM:ADMIN_ALL}）不需要在權限字典裡定義。 */
    public boolean isWildcard() {
        return ADMIN_ALL.equals(permissionCode);
    }

    public String authority() {
        return systemCode + ":" + permissionCode;
    }

    @Override
    public String toString() {
        return authority();
    }
}
