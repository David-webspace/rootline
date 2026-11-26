package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.permission.PermissionDefinition;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.PermissionDictionary;
import dev.rootline.auth.application.shared.Views.PermissionView;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 把權限 key 補上字典裡的顯示名稱；萬用權限與尚未同步到字典的權限給預設名稱。 */
@Component
class PermissionNames {

    private final PermissionDictionary dictionary;

    PermissionNames(PermissionDictionary dictionary) {
        this.dictionary = dictionary;
    }

    List<PermissionView> describe(TenantId tenantId, Collection<PermissionKey> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        Map<PermissionKey, PermissionDefinition> defined = dictionary.findAll(tenantId, keys);
        return keys.stream()
                .sorted(Comparator.comparing(PermissionKey::authority))
                .map(key -> new PermissionView(key.systemCode(), key.permissionCode(), nameOf(key, defined.get(key))))
                .toList();
    }

    private static String nameOf(PermissionKey key, PermissionDefinition definition) {
        if (definition != null) {
            return definition.name();
        }
        if (key.equals(PermissionKey.SUPER_ADMIN)) {
            return "All permissions";
        }
        if (key.isWildcard()) {
            return "All " + key.systemCode() + " permissions";
        }
        return key.authority();
    }
}
