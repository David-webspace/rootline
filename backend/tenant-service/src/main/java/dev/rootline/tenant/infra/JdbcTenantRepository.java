package dev.rootline.tenant.infra;

import dev.rootline.tenant.application.domain.shared.DomainException;
import dev.rootline.tenant.application.domain.shared.TenantId;
import dev.rootline.tenant.application.domain.tenant.PlanType;
import dev.rootline.tenant.application.domain.tenant.Tenant;
import dev.rootline.tenant.application.domain.tenant.TenantStatus;
import dev.rootline.tenant.application.port.TenantPorts.Page;
import dev.rootline.tenant.application.port.TenantPorts.TenantRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
class JdbcTenantRepository implements TenantRepository {

    private static final RowMapper<Tenant> MAPPER = (rs, i) -> Tenant.rehydrate(TenantId.of(rs.getString("tenant_id")),
            rs.getString("company_name"), PlanType.valueOf(rs.getString("plan")),
            TenantStatus.valueOf(rs.getString("status")), rs.getString("suspend_reason"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant(), rs.getLong("version"));

    private final JdbcTemplate jdbc;

    JdbcTenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Tenant> find(TenantId id) {
        return jdbc.query("SELECT * FROM tenants WHERE tenant_id = ?", MAPPER, id.value()).stream().findFirst();
    }

    @Override
    public Page<Tenant> search(String keyword, TenantStatus status, int page, int size) {
        String pattern = keyword == null || keyword.isBlank() ? "%"
                : "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String statusFilter = status == null ? null : status.name();
        String where = """
                WHERE (tenant_id ILIKE ? ESCAPE '\\' OR company_name ILIKE ? ESCAPE '\\')
                  AND (?::text IS NULL OR status = ?)
                """;
        Long total = jdbc.queryForObject("SELECT count(*) FROM tenants " + where, Long.class, pattern, pattern,
                statusFilter, statusFilter);
        List<Tenant> content = jdbc.query("SELECT * FROM tenants " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                MAPPER, pattern, pattern, statusFilter, statusFilter, size, (long) page * size);
        return new Page<>(content, total == null ? 0 : total, page, size);
    }

    @Override
    public void insert(Tenant t) {
        try {
            jdbc.update("""
                    INSERT INTO tenants (tenant_id, company_name, plan, status, suspend_reason, created_at, expires_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, t.id().value(), t.companyName(), t.plan().name(), t.status().name(), t.suspendReason(),
                    Timestamp.from(t.createdAt()), Timestamp.from(t.expiresAt()), t.version());
        } catch (DuplicateKeyException e) {
            throw DomainException.conflict("TENANT_ID_TAKEN", "Tenant " + t.id() + " already exists");
        }
    }

    @Override
    public void update(Tenant t, long expectedVersion) {
        int updated = jdbc.update("""
                UPDATE tenants SET company_name = ?, plan = ?, status = ?, suspend_reason = ?, version = ?, updated_at = now()
                WHERE tenant_id = ? AND version = ?
                """, t.companyName(), t.plan().name(), t.status().name(), t.suspendReason(), t.version(), t.id().value(),
                expectedVersion);
        if (updated == 0) {
            throw DomainException.conflict("CONCURRENT_MODIFICATION", "Tenant " + t.id() + " was modified concurrently");
        }
    }
}
