package dev.rootline.auth.application.domain.role;

import dev.rootline.auth.application.domain.role.RoleEvent.RoleCreated;
import dev.rootline.auth.application.domain.role.RoleEvent.RolePermissionGranted;
import dev.rootline.auth.application.domain.role.RoleEvent.RolePermissionRevoked;
import dev.rootline.auth.application.domain.role.RoleEvent.RoleRenamed;
import dev.rootline.auth.application.domain.shared.AggregateRoot;
import dev.rootline.auth.application.domain.shared.BusinessCode;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 角色聚合根：一組權限的集合。
 * System root 角色（例如租戶開通時建立的 {@code TENANT_ADMIN}）不能改名、不能增減權限，
 * 避免管理員把自己鎖在門外。
 */
public class Role extends AggregateRoot {

    private final UUID id;
    private final TenantId tenantId;
    private final RoleCode code;
    private String name;
    private final boolean systemRoot;
    private final Set<PermissionKey> permissions;

    private Role(UUID id, TenantId tenantId, RoleCode code, String name, boolean systemRoot,
                 Set<PermissionKey> permissions) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.code = Objects.requireNonNull(code);
        this.name = BusinessCode.requireName(name, "ROLE_NAME_INVALID", "Role name");
        this.systemRoot = systemRoot;
        this.permissions = new LinkedHashSet<>(permissions);
    }

    public static Role createCustom(TenantId tenantId, RoleCode code, String name) {
        return create(tenantId, code, name, false, Set.of());
    }

    public static Role createSystemRoot(TenantId tenantId, RoleCode code, String name, Set<PermissionKey> permissions) {
        return create(tenantId, code, name, true, permissions);
    }

    private static Role create(TenantId tenantId, RoleCode code, String name, boolean systemRoot,
                               Set<PermissionKey> permissions) {
        Role role = new Role(UUID.randomUUID(), tenantId, code, name, systemRoot, permissions);
        role.raise(new RoleCreated(UUID.randomUUID(), Instant.now(), tenantId.value(), role.id.toString(),
                code.value(), role.name, systemRoot,
                role.permissions.stream().map(PermissionKey::authority).toList()));
        return role;
    }

    public static Role rehydrate(UUID id, TenantId tenantId, RoleCode code, String name, boolean systemRoot,
                                 Set<PermissionKey> permissions) {
        return new Role(id, tenantId, code, name, systemRoot, permissions);
    }

    public void rename(String newName) {
        requireMutable("rename");
        String normalized = BusinessCode.requireName(newName, "ROLE_NAME_INVALID", "Role name");
        if (normalized.equals(name)) {
            return;
        }
        name = normalized;
        raise(new RoleRenamed(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(), code.value(), name));
    }

    public void grant(PermissionKey permission) {
        requireMutable("change permissions of");
        if (permissions.add(Objects.requireNonNull(permission))) {
            raise(new RolePermissionGranted(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    code.value(), permission.authority()));
        }
    }

    public void revoke(PermissionKey permission) {
        requireMutable("change permissions of");
        if (permissions.remove(permission)) {
            raise(new RolePermissionRevoked(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    code.value(), permission.authority()));
        }
    }

    private void requireMutable(String action) {
        if (systemRoot) {
            throw DomainException.violation("SYSTEM_ROLE_IMMUTABLE", "Cannot " + action + " system role " + code);
        }
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public RoleCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public boolean isSystemRoot() {
        return systemRoot;
    }

    public Set<PermissionKey> permissions() {
        return Collections.unmodifiableSet(permissions);
    }
}
