package dev.rootline.auth.infra.persistence;

import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.role.Role;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.RoleRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class JpaRoleRepository implements RoleRepository {

    private final RoleJpaRepository jpa;

    JpaRoleRepository(RoleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Role> findByCode(TenantId tenantId, RoleCode code) {
        return jpa.findByTenantIdAndRoleCode(tenantId.value(), code.value()).map(JpaRoleRepository::toDomain);
    }

    @Override
    public List<Role> findByCodes(TenantId tenantId, Collection<RoleCode> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        List<String> values = codes.stream().map(RoleCode::value).toList();
        return jpa.findByTenantIdAndRoleCodeIn(tenantId.value(), values).stream()
                .map(JpaRoleRepository::toDomain).toList();
    }

    @Override
    public List<Role> findAll(TenantId tenantId) {
        return jpa.findByTenantId(tenantId.value()).stream().map(JpaRoleRepository::toDomain).toList();
    }

    @Override
    public boolean exists(TenantId tenantId, RoleCode code) {
        return jpa.existsByTenantIdAndRoleCode(tenantId.value(), code.value());
    }

    @Override
    public void save(Role role) {
        RoleEntity entity = jpa.findById(role.id()).orElseGet(() -> new RoleEntity(role.id()));
        entity.tenantId = role.tenantId().value();
        entity.roleCode = role.code().value();
        entity.roleName = role.name();
        entity.systemRoot = role.isSystemRoot();
        entity.permissions.clear();
        role.permissions().forEach(p -> entity.permissions.add(
                new PermissionEmbeddable(p.systemCode(), p.permissionCode())));
        jpa.save(entity);
    }

    private static Role toDomain(RoleEntity e) {
        return Role.rehydrate(e.id, TenantId.of(e.tenantId), RoleCode.of(e.roleCode), e.roleName, e.systemRoot,
                e.permissions.stream()
                        .map(p -> new PermissionKey(p.systemCode, p.permissionCode))
                        .collect(Collectors.toSet()));
    }
}
