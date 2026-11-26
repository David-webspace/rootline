package dev.rootline.auth.infra.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByTenantIdAndRoleCode(String tenantId, String roleCode);

    List<RoleEntity> findByTenantIdAndRoleCodeIn(String tenantId, Collection<String> roleCodes);

    List<RoleEntity> findByTenantId(String tenantId);

    boolean existsByTenantIdAndRoleCode(String tenantId, String roleCode);
}
