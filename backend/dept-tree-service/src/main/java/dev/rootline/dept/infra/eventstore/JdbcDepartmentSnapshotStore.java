package dev.rootline.dept.infra.eventstore;

import dev.rootline.dept.application.domain.dept.Department.Snapshot;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentSnapshotStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class JdbcDepartmentSnapshotStore implements DepartmentSnapshotStore {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    JdbcDepartmentSnapshotStore(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Snapshot> latest(TenantId tenantId, DepartmentId departmentId) {
        return jdbc.query("""
                SELECT state::text FROM department_snapshots WHERE tenant_id = ? AND aggregate_id = ?
                ORDER BY version DESC LIMIT 1
                """, (rs, i) -> json.readValue(rs.getString(1), Snapshot.class),
                tenantId.value(), departmentId.value()).stream().findFirst();
    }

    @Override
    public Optional<Snapshot> latestAtOrBefore(TenantId tenantId, DepartmentId departmentId, Instant instant) {
        return jdbc.query("""
                SELECT state::text FROM department_snapshots
                WHERE tenant_id = ? AND aggregate_id = ? AND last_event_at <= ?
                ORDER BY version DESC LIMIT 1
                """, (rs, i) -> json.readValue(rs.getString(1), Snapshot.class),
                tenantId.value(), departmentId.value(), Timestamp.from(instant)).stream().findFirst();
    }

    /** 同一版本重複存（例如重試）直接略過。 */
    @Override
    public void save(Snapshot snapshot) {
        jdbc.update("""
                INSERT INTO department_snapshots (tenant_id, aggregate_id, version, last_event_at, state)
                VALUES (?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING
                """, snapshot.tenantId(), snapshot.id(), snapshot.version(),
                Timestamp.from(snapshot.lastModifiedAt()), json.writeValueAsString(snapshot));
    }
}
