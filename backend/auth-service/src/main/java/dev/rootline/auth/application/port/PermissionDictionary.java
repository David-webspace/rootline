package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.permission.PermissionDefinition;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.shared.TenantId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 權限字典投影（來源是 dept-tree-service 的權限定義事件）。 */
public interface PermissionDictionary {

    Optional<PermissionDefinition> find(TenantId tenantId, PermissionKey key);

    List<PermissionDefinition> findAll(TenantId tenantId);

    Map<PermissionKey, PermissionDefinition> findAll(TenantId tenantId, Collection<PermissionKey> keys);

    /**
     * 只有版本比現有的新才寫入。
     *
     * @return 是否真的寫入（false 代表是重送或亂序的舊事件）
     */
    boolean upsertIfNewer(PermissionDefinition definition);
}
