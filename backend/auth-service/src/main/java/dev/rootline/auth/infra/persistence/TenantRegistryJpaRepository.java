package dev.rootline.auth.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRegistryJpaRepository extends JpaRepository<TenantRegistryEntity, String> {
}
