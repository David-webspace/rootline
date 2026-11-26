package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.role.Role;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    Optional<Role> findByCode(TenantId tenantId, RoleCode code);

    List<Role> findByCodes(TenantId tenantId, Collection<RoleCode> codes);

    List<Role> findAll(TenantId tenantId);

    boolean exists(TenantId tenantId, RoleCode code);

    void save(Role role);
}
