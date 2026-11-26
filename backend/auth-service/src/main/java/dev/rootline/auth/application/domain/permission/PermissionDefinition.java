package dev.rootline.auth.application.domain.permission;

import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.shared.TenantId;
import java.util.Objects;

/**
 * 權限字典的一筆定義。權威來源是 dept-tree-service，auth 只保存投影；
 * {@code version} 是來源端的邏輯時鐘，用來擋掉重送與亂序到達的舊事件。
 */
public record PermissionDefinition(TenantId tenantId, PermissionKey key, String name, String description,
                                   long version) {

    public PermissionDefinition {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        description = description == null ? "" : description;
    }

    public boolean isNewerThan(PermissionDefinition other) {
        return other == null || version > other.version;
    }
}
