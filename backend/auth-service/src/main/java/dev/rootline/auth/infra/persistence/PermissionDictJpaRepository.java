package dev.rootline.auth.infra.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionDictJpaRepository extends JpaRepository<PermissionDictEntity, PermissionDictEntity.Key> {

    List<PermissionDictEntity> findByIdTenantId(String tenantId);
}
