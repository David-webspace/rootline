package dev.rootline.dept.application.domain.permission;

import dev.rootline.dept.application.domain.permission.PermissionEvent.PermissionDefined;
import dev.rootline.dept.application.domain.permission.PermissionEvent.PermissionUpdated;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 權限定義聚合：一個「系統代碼 + 權限代碼」的顯示名稱與說明。
 * 代碼建立後不可改；{@code ADMIN_ALL} 是保留字（萬用權限），不能被定義。
 */
public final class PermissionDefinition {

    private static final Pattern SYSTEM = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");
    public static final String RESERVED = "ADMIN_ALL";

    private final TenantId tenantId;
    private final String systemCode;
    private final String permissionCode;
    private String name;
    private String description;
    private long version;
    private final List<PermissionEvent> pending = new ArrayList<>();

    private PermissionDefinition(TenantId tenantId, String systemCode, String permissionCode, String name,
                                 String description, long version) {
        this.tenantId = tenantId;
        this.systemCode = systemCode;
        this.permissionCode = permissionCode;
        this.name = name;
        this.description = description;
        this.version = version;
    }

    public static PermissionDefinition define(TenantId tenantId, String systemCode, String permissionCode,
                                              String name, String description, String operator) {
        String system = normalize(systemCode, SYSTEM, "System code");
        String code = normalize(permissionCode, CODE, "Permission code");
        if (RESERVED.equals(code)) {
            throw DomainException.invalid("PERMISSION_CODE_RESERVED", RESERVED + " is reserved for wildcard grants");
        }
        PermissionDefinition definition = new PermissionDefinition(tenantId, system, code, validName(name),
                validDescription(description), 1);
        definition.pending.add(new PermissionDefined(UUID.randomUUID(), Instant.now(), tenantId.value(), system, code,
                definition.name, definition.description, 1, requireOperator(operator)));
        return definition;
    }

    public static PermissionDefinition rehydrate(TenantId tenantId, String systemCode, String permissionCode,
                                                 String name, String description, long version) {
        return new PermissionDefinition(tenantId, systemCode, permissionCode, name, description, version);
    }

    public void update(String newName, String newDescription, String operator) {
        String name = validName(newName);
        String description = validDescription(newDescription);
        if (name.equals(this.name) && description.equals(this.description)) {
            return;
        }
        this.name = name;
        this.description = description;
        this.version++;
        pending.add(new PermissionUpdated(UUID.randomUUID(), Instant.now(), tenantId.value(), systemCode,
                permissionCode, name, description, version, requireOperator(operator)));
    }

    public List<PermissionEvent> pullEvents() {
        List<PermissionEvent> events = List.copyOf(pending);
        pending.clear();
        return events;
    }

    private static String normalize(String raw, Pattern format, String label) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!format.matcher(value).matches()) {
            throw DomainException.invalid("PERMISSION_INVALID", label + " must start with a letter and use A-Z, 0-9, '_'");
        }
        return value;
    }

    private static String validName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw DomainException.invalid("PERMISSION_NAME_INVALID", "Permission name is required (max 100 chars)");
        }
        return name.trim();
    }

    private static String validDescription(String description) {
        String value = description == null ? "" : description.trim();
        if (value.length() > 500) {
            throw DomainException.invalid("PERMISSION_DESCRIPTION_INVALID", "Description must be at most 500 chars");
        }
        return value;
    }

    private static String requireOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            throw DomainException.invalid("OPERATOR_REQUIRED", "Operator is required for the audit trail");
        }
        return operator;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String systemCode() {
        return systemCode;
    }

    public String permissionCode() {
        return permissionCode;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public long version() {
        return version;
    }
}
