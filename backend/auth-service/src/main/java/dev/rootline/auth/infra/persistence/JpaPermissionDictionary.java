package dev.rootline.auth.infra.persistence;

import dev.rootline.auth.application.domain.permission.PermissionDefinition;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.PermissionDictionary;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class JpaPermissionDictionary implements PermissionDictionary {

    private final PermissionDictJpaRepository jpa;

    JpaPermissionDictionary(PermissionDictJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<PermissionDefinition> find(TenantId tenantId, PermissionKey key) {
        return jpa.findById(keyOf(tenantId, key)).map(JpaPermissionDictionary::toDomain);
    }

    @Override
    public List<PermissionDefinition> findAll(TenantId tenantId) {
        return jpa.findByIdTenantId(tenantId.value()).stream().map(JpaPermissionDictionary::toDomain).toList();
    }

    @Override
    public Map<PermissionKey, PermissionDefinition> findAll(TenantId tenantId, Collection<PermissionKey> keys) {
        List<PermissionDictEntity.Key> ids = keys.stream().map(k -> keyOf(tenantId, k)).toList();
        return jpa.findAllById(ids).stream()
                .map(JpaPermissionDictionary::toDomain)
                .collect(Collectors.toMap(PermissionDefinition::key, Function.identity()));
    }

    @Override
    public boolean upsertIfNewer(PermissionDefinition definition) {
        PermissionDictEntity.Key id = keyOf(definition.tenantId(), definition.key());
        Optional<PermissionDictEntity> existing = jpa.findById(id);
        if (existing.isPresent() && !definition.isNewerThan(toDomain(existing.get()))) {
            return false;
        }
        PermissionDictEntity entity = existing.orElseGet(() -> new PermissionDictEntity(id));
        entity.permissionName = definition.name();
        entity.description = definition.description();
        entity.version = definition.version();
        entity.updatedAt = Instant.now();
        jpa.save(entity);
        return true;
    }

    private static PermissionDictEntity.Key keyOf(TenantId tenantId, PermissionKey key) {
        return new PermissionDictEntity.Key(tenantId.value(), key.systemCode(), key.permissionCode());
    }

    private static PermissionDefinition toDomain(PermissionDictEntity e) {
        return new PermissionDefinition(TenantId.of(e.id.tenantId),
                new PermissionKey(e.id.systemCode, e.id.permissionCode), e.permissionName, e.description, e.version);
    }
}
