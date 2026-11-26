package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.role.Role;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.DomainEventOutbox;
import dev.rootline.auth.application.port.PermissionDictionary;
import dev.rootline.auth.application.port.RoleRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoleCommandService {

    private final RoleRepository roles;
    private final PermissionDictionary dictionary;
    private final DomainEventOutbox outbox;

    public RoleCommandService(RoleRepository roles, PermissionDictionary dictionary, DomainEventOutbox outbox) {
        this.roles = roles;
        this.dictionary = dictionary;
        this.outbox = outbox;
    }

    public String create(TenantId tenantId, String roleCode, String roleName) {
        RoleCode code = RoleCode.of(roleCode);
        requireUnique(tenantId, code);
        Role role = Role.createCustom(tenantId, code, roleName);
        persist(role);
        return role.code().value();
    }

    /** 租戶開通時建立的最高權限角色，之後不可修改。 */
    public String createSystemRoot(TenantId tenantId, String roleCode, String roleName, Set<PermissionKey> permissions) {
        RoleCode code = RoleCode.of(roleCode);
        requireUnique(tenantId, code);
        Role role = Role.createSystemRoot(tenantId, code, roleName, permissions);
        persist(role);
        return role.code().value();
    }

    public void rename(TenantId tenantId, String roleCode, String newName) {
        Role role = load(tenantId, roleCode);
        role.rename(newName);
        persist(role);
    }

    /** 只能授予權限字典裡已定義的權限；萬用的 ADMIN_ALL 例外。 */
    public void grantPermission(TenantId tenantId, String roleCode, String systemCode, String permissionCode) {
        Role role = load(tenantId, roleCode);
        PermissionKey key = new PermissionKey(systemCode, permissionCode);
        if (!key.isWildcard() && dictionary.find(tenantId, key).isEmpty()) {
            throw DomainException.notFound("PERMISSION_NOT_DEFINED", "Permission " + key + " is not defined");
        }
        role.grant(key);
        persist(role);
    }

    public void revokePermission(TenantId tenantId, String roleCode, String systemCode, String permissionCode) {
        Role role = load(tenantId, roleCode);
        role.revoke(new PermissionKey(systemCode, permissionCode));
        persist(role);
    }

    private void requireUnique(TenantId tenantId, RoleCode code) {
        if (roles.exists(tenantId, code)) {
            throw DomainException.conflict("ROLE_CODE_TAKEN", "Role " + code + " already exists");
        }
    }

    private Role load(TenantId tenantId, String roleCode) {
        RoleCode code = RoleCode.of(roleCode);
        return roles.findByCode(tenantId, code)
                .orElseThrow(() -> DomainException.notFound("ROLE_NOT_FOUND", "Role " + code + " not found"));
    }

    private void persist(Role role) {
        roles.save(role);
        outbox.append(role.pullEvents());
    }
}
