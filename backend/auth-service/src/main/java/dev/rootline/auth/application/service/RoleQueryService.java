package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.role.Role;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.RoleRepository;
import dev.rootline.auth.application.shared.Views.RoleView;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleQueryService {

    private final RoleRepository roles;
    private final PermissionNames permissionNames;

    public RoleQueryService(RoleRepository roles, PermissionNames permissionNames) {
        this.roles = roles;
        this.permissionNames = permissionNames;
    }

    public RoleView get(TenantId tenantId, String roleCode) {
        RoleCode code = RoleCode.of(roleCode);
        Role role = roles.findByCode(tenantId, code)
                .orElseThrow(() -> DomainException.notFound("ROLE_NOT_FOUND", "Role " + code + " not found"));
        return toView(tenantId, role);
    }

    public List<RoleView> list(TenantId tenantId) {
        return roles.findAll(tenantId).stream()
                .sorted(Comparator.comparing(r -> r.code().value()))
                .map(role -> toView(tenantId, role))
                .toList();
    }

    private RoleView toView(TenantId tenantId, Role role) {
        return new RoleView(role.id().toString(), role.code().value(), role.name(), role.isSystemRoot(),
                permissionNames.describe(tenantId, role.permissions()));
    }
}
