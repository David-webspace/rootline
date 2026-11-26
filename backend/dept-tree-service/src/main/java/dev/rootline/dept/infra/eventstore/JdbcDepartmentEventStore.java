package dev.rootline.dept.infra.eventstore;

import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentEventStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL 事件庫。payload 是事件 record 的 JSON；event_type 對回 sealed interface 的實作類別。
 * 新增事件類型只要在 {@link DepartmentEvent} 加一個 record，這裡不用改。
 */
@Component
class JdbcDepartmentEventStore implements DepartmentEventStore {

    private static final Map<String, Class<?>> TYPES = Arrays.stream(DepartmentEvent.class.getPermittedSubclasses())
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, c -> c));

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    JdbcDepartmentEventStore(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<DepartmentEvent> load(TenantId tenantId, DepartmentId departmentId) {
        return jdbc.query("""
                SELECT event_type, payload::text AS payload FROM department_events
                WHERE tenant_id = ? AND aggregate_id = ? ORDER BY version
                """, (rs, i) -> deserialize(rs), tenantId.value(), departmentId.value());
    }

    @Override
    public List<DepartmentEvent> loadAfter(TenantId tenantId, DepartmentId departmentId, long afterVersion) {
        return jdbc.query("""
                SELECT event_type, payload::text AS payload FROM department_events
                WHERE tenant_id = ? AND aggregate_id = ? AND version > ? ORDER BY version
                """, (rs, i) -> deserialize(rs), tenantId.value(), departmentId.value(), afterVersion);
    }

    @Override
    public List<DepartmentEvent> loadUpTo(TenantId tenantId, DepartmentId departmentId, long afterVersion,
                                          Instant until) {
        return jdbc.query("""
                SELECT event_type, payload::text AS payload FROM department_events
                WHERE tenant_id = ? AND aggregate_id = ? AND version > ? AND occurred_at <= ? ORDER BY version
                """, (rs, i) -> deserialize(rs), tenantId.value(), departmentId.value(), afterVersion,
                Timestamp.from(until));
    }

    @Override
    public List<StoredEvent> history(TenantId tenantId, DepartmentId departmentId) {
        return jdbc.query("""
                SELECT version, event_type, payload::text AS payload FROM department_events
                WHERE tenant_id = ? AND aggregate_id = ? ORDER BY version
                """, (rs, i) -> new StoredEvent(rs.getLong("version"), deserialize(rs)),
                tenantId.value(), departmentId.value());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(TenantId tenantId, DepartmentId departmentId, long expectedVersion,
                       List<DepartmentEvent> events) {
        long version = expectedVersion;
        try {
            for (DepartmentEvent event : events) {
                version++;
                jdbc.update("""
                        INSERT INTO department_events
                            (tenant_id, aggregate_id, version, event_id, event_type, payload, operator, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                        """,
                        tenantId.value(), departmentId.value(), version, event.eventId(), event.eventType(),
                        json.writeValueAsString(event), event.operator(), Timestamp.from(event.occurredAt()));
            }
        } catch (DuplicateKeyException e) {
            throw DomainException.conflict("CONCURRENT_MODIFICATION",
                    "Department " + departmentId + " was modified concurrently; reload and retry");
        }
    }

    @Override
    public void forEachInTenant(TenantId tenantId, Consumer<DepartmentEvent> consumer) {
        jdbc.query(connection -> {
            var statement = connection.prepareStatement(
                    "SELECT event_type, payload::text AS payload FROM department_events WHERE tenant_id = ? ORDER BY seq");
            statement.setString(1, tenantId.value());
            statement.setFetchSize(500);
            return statement;
        }, rs -> {
            consumer.accept(deserialize(rs));
        });
    }

    private DepartmentEvent deserialize(ResultSet rs) throws SQLException {
        String type = rs.getString("event_type");
        Class<?> target = TYPES.get(type);
        if (target == null) {
            throw new IllegalStateException("Unknown department event type in store: " + type);
        }
        return (DepartmentEvent) json.readValue(rs.getString("payload"), target);
    }
}
