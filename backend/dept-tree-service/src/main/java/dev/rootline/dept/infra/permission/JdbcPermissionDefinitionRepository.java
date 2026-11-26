package dev.rootline.dept.infra.permission;

import dev.rootline.dept.application.domain.permission.PermissionDefinition;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.PermissionDefinitionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
class JdbcPermissionDefinitionRepository implements PermissionDefinitionRepository {

    private final JdbcTemplate jdbc;

    JdbcPermissionDefinitionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private RowMapper<PermissionDefinition> mapper(TenantId tenantId) {
        return (rs, i) -> PermissionDefinition.rehydrate(tenantId, rs.getString("system_code"),
                rs.getString("permission_code"), rs.getString("permission_name"), rs.getString("description"),
                rs.getLong("version"));
    }

    @Override
    public Optional<PermissionDefinition> find(TenantId tenantId, String systemCode, String permissionCode) {
        return jdbc.query("""
                SELECT * FROM permission_definitions WHERE tenant_id = ? AND system_code = ? AND permission_code = ?
                """, mapper(tenantId), tenantId.value(), upper(systemCode), upper(permissionCode)).stream().findFirst();
    }

    @Override
    public List<PermissionDefinition> findAll(TenantId tenantId) {
        return jdbc.query("""
                SELECT * FROM permission_definitions WHERE tenant_id = ? ORDER BY system_code, permission_code
                """, mapper(tenantId), tenantId.value());
    }

    @Override
    public void insert(PermissionDefinition d, String operator) {
        try {
            jdbc.update("""
                    INSERT INTO permission_definitions
                        (tenant_id, system_code, permission_code, permission_name, description, version, updated_at, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, now(), ?)
                    """, d.tenantId().value(), d.systemCode(), d.permissionCode(), d.name(), d.description(),
                    d.version(), operator);
        } catch (DuplicateKeyException e) {
            throw DomainException.conflict("PERMISSION_ALREADY_DEFINED",
                    d.systemCode() + ":" + d.permissionCode() + " is already defined");
        }
    }

    @Override
    public void update(PermissionDefinition d, long expectedVersion, String operator) {
        int updated = jdbc.update("""
                UPDATE permission_definitions SET permission_name = ?, description = ?, version = ?,
                       updated_at = now(), updated_by = ?
                WHERE tenant_id = ? AND system_code = ? AND permission_code = ? AND version = ?
                """, d.name(), d.description(), d.version(), operator, d.tenantId().value(), d.systemCode(),
                d.permissionCode(), expectedVersion);
        if (updated == 0) {
            throw DomainException.conflict("CONCURRENT_MODIFICATION",
                    d.systemCode() + ":" + d.permissionCode() + " was modified concurrently; reload and retry");
        }
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
