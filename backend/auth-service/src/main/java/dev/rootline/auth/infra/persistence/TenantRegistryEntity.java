package dev.rootline.auth.infra.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_registry")
public class TenantRegistryEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";

    @Id
    String tenantId;

    String status;
    Instant updatedAt;

    protected TenantRegistryEntity() {
    }

    public TenantRegistryEntity(String tenantId, String status) {
        this.tenantId = tenantId;
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
