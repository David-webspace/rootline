package dev.rootline.dept.application.domain.rule;

import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 動態 API 規則：「這個租戶裡，符合 method + path pattern 的請求，還要多具備某個權限」。
 * 只能收緊不能放寬（見 shared-kernel 的 PermissionGuardInterceptor）。
 * path pattern 用 Ant 風格（{@code *} 一層、{@code **} 多層），且必須在 {@code /api/} 底下。
 */
public final class ApiRule {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "*");
    private static final Pattern PATH = Pattern.compile("^/api/[A-Za-z0-9_\\-*/{}.]*$");
    private static final Pattern PERMISSION = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}:[A-Z][A-Z0-9_]{1,63}$");

    private final UUID id;
    private final TenantId tenantId;
    private String method;
    private String pathPattern;
    private String requiredPermission;
    private boolean enabled;
    private String description;
    private long version;

    private ApiRule(UUID id, TenantId tenantId, String method, String pathPattern, String requiredPermission,
                    boolean enabled, String description, long version) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.method = method;
        this.pathPattern = pathPattern;
        this.requiredPermission = requiredPermission;
        this.enabled = enabled;
        this.description = description;
        this.version = version;
    }

    public static ApiRule create(TenantId tenantId, String method, String pathPattern, String requiredPermission,
                                 String description) {
        return new ApiRule(UUID.randomUUID(), tenantId, validMethod(method), validPath(pathPattern),
                validPermission(requiredPermission), true, validDescription(description), 0);
    }

    public static ApiRule rehydrate(UUID id, TenantId tenantId, String method, String pathPattern,
                                    String requiredPermission, boolean enabled, String description, long version) {
        return new ApiRule(id, tenantId, method, pathPattern, requiredPermission, enabled, description, version);
    }

    public void change(String method, String pathPattern, String requiredPermission, String description) {
        this.method = validMethod(method);
        this.pathPattern = validPath(pathPattern);
        this.requiredPermission = validPermission(requiredPermission);
        this.description = validDescription(description);
        this.version++;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            this.version++;
        }
    }

    private static String validMethod(String method) {
        String value = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(value)) {
            throw DomainException.invalid("API_RULE_METHOD_INVALID", "Method must be one of " + METHODS);
        }
        return value;
    }

    private static String validPath(String path) {
        String value = path == null ? "" : path.trim();
        if (value.length() > 200 || !PATH.matcher(value).matches() || value.contains("//")) {
            throw DomainException.invalid("API_RULE_PATH_INVALID",
                    "Path pattern must start with /api/ and use Ant-style wildcards, e.g. /api/users/**");
        }
        return value;
    }

    private static String validPermission(String permission) {
        String value = permission == null ? "" : permission.trim().toUpperCase(Locale.ROOT);
        if (!PERMISSION.matcher(value).matches()) {
            throw DomainException.invalid("API_RULE_PERMISSION_INVALID", "Permission must look like SYSTEM:PERMISSION");
        }
        return value;
    }

    private static String validDescription(String description) {
        String value = description == null ? "" : description.trim();
        if (value.length() > 300) {
            throw DomainException.invalid("API_RULE_DESCRIPTION_INVALID", "Description must be at most 300 chars");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String method() {
        return method;
    }

    public String pathPattern() {
        return pathPattern;
    }

    public String requiredPermission() {
        return requiredPermission;
    }

    public boolean enabled() {
        return enabled;
    }

    public String description() {
        return description;
    }

    public long version() {
        return version;
    }
}
