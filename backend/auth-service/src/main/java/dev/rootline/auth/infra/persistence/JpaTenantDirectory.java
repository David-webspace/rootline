package dev.rootline.auth.infra.persistence;

import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.TenantDirectory;
import org.springframework.stereotype.Repository;

@Repository
class JpaTenantDirectory implements TenantDirectory {

    private final TenantRegistryJpaRepository jpa;

    JpaTenantDirectory(TenantRegistryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean isActive(TenantId tenantId) {
        return jpa.findById(tenantId.value())
                .map(t -> TenantRegistryEntity.ACTIVE.equals(t.status))
                .orElse(false);
    }

    @Override
    public void setActive(TenantId tenantId, boolean active) {
        jpa.save(new TenantRegistryEntity(tenantId.value(),
                active ? TenantRegistryEntity.ACTIVE : TenantRegistryEntity.SUSPENDED));
    }
}
