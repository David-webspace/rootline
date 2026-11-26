package dev.rootline.dept.infra.rule;

import dev.rootline.dept.application.domain.rule.ApiRule;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.ApiRuleRepository;
import dev.rootline.dept.application.port.DepartmentReadModel.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
class JdbcApiRuleRepository implements ApiRuleRepository {

    private final JdbcTemplate jdbc;

    JdbcApiRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static RowMapper<ApiRule> mapper(TenantId tenantId) {
        return (rs, i) -> ApiRule.rehydrate((UUID) rs.getObject("id"), tenantId, rs.getString("http_method"),
                rs.getString("path_pattern"), rs.getString("required_permission"), rs.getBoolean("enabled"),
                rs.getString("description"), rs.getLong("version"));
    }

    @Override
    public Optional<ApiRule> find(TenantId tenantId, UUID id) {
        return jdbc.query("SELECT * FROM api_resource_rules WHERE tenant_id = ? AND id = ?", mapper(tenantId),
                tenantId.value(), id).stream().findFirst();
    }

    @Override
    public Page<ApiRule> page(TenantId tenantId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM api_resource_rules WHERE tenant_id = ?", Long.class,
                tenantId.value());
        List<ApiRule> content = jdbc.query("""
                SELECT * FROM api_resource_rules WHERE tenant_id = ?
                ORDER BY path_pattern, http_method LIMIT ? OFFSET ?
                """, mapper(tenantId), tenantId.value(), size, (long) page * size);
        return new Page<>(content, total == null ? 0 : total, page, size);
    }

    @Override
    public List<ApiRule> enabled(TenantId tenantId) {
        return jdbc.query("""
                SELECT * FROM api_resource_rules WHERE tenant_id = ? AND enabled ORDER BY path_pattern, http_method
                """, mapper(tenantId), tenantId.value());
    }

    @Override
    public void insert(ApiRule r, String operator) {
        try {
            jdbc.update("""
                    INSERT INTO api_resource_rules (id, tenant_id, http_method, path_pattern, required_permission,
                                                    enabled, description, version, updated_at, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), ?)
                    """, r.id(), r.tenantId().value(), r.method(), r.pathPattern(), r.requiredPermission(), r.enabled(),
                    r.description(), r.version(), operator);
        } catch (DuplicateKeyException e) {
            throw duplicate();
        }
    }

    @Override
    public void update(ApiRule r, long expectedVersion, String operator) {
        int updated;
        try {
            updated = jdbc.update("""
                    UPDATE api_resource_rules SET http_method = ?, path_pattern = ?, required_permission = ?,
                           enabled = ?, description = ?, version = ?, updated_at = now(), updated_by = ?
                    WHERE tenant_id = ? AND id = ? AND version = ?
                    """, r.method(), r.pathPattern(), r.requiredPermission(), r.enabled(), r.description(), r.version(),
                    operator, r.tenantId().value(), r.id(), expectedVersion);
        } catch (DuplicateKeyException e) {
            throw duplicate();
        }
        if (updated == 0) {
            throw DomainException.conflict("CONCURRENT_MODIFICATION", "API rule was modified concurrently; reload and retry");
        }
    }

    private static DomainException duplicate() {
        return DomainException.conflict("API_RULE_DUPLICATE", "An identical rule already exists");
    }
}
