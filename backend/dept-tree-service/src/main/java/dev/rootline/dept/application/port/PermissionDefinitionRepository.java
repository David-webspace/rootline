package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.permission.PermissionDefinition;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.util.List;
import java.util.Optional;

public interface PermissionDefinitionRepository {

    Optional<PermissionDefinition> find(TenantId tenantId, String systemCode, String permissionCode);

    List<PermissionDefinition> findAll(TenantId tenantId);

    /** 新增；已存在時丟出 CONFLICT。 */
    void insert(PermissionDefinition definition, String operator);

    /** 樂觀鎖更新：資料庫裡的版本必須是 {@code expectedVersion}，否則丟出 CONFLICT。 */
    void update(PermissionDefinition definition, long expectedVersion, String operator);
}
