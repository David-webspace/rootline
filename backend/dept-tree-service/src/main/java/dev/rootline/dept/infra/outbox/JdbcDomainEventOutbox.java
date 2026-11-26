package dev.rootline.dept.infra.outbox;

import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.permission.PermissionEvent;
import dev.rootline.dept.application.port.DomainEventOutbox;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 事件寫進 outbox_events，由 outbox-relay 推到 Kafka。aggregate_id 也是 Kafka 的 message key。 */
@Component
class JdbcDomainEventOutbox implements DomainEventOutbox {

    static final String DEPARTMENT_TOPIC = "rootline.dept.department";
    static final String PERMISSION_TOPIC = "rootline.dept.permission";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    JdbcDomainEventOutbox(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Collection<? extends DepartmentEvent> events) {
        for (DepartmentEvent e : events) {
            insert(e.eventId(), "Department", e.departmentId(), e.eventType(), DEPARTMENT_TOPIC, e, e.occurredAt());
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendPermissionEvents(Collection<? extends PermissionEvent> events) {
        for (PermissionEvent e : events) {
            insert(e.eventId(), "Permission", e.aggregateId(), e.eventType(), PERMISSION_TOPIC, e, e.occurredAt());
        }
    }

    private void insert(UUID id, String aggregateType, String aggregateId, String eventType, String topic,
                        Object payload, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """, id, aggregateType, aggregateId, eventType, topic, json.writeValueAsString(payload),
                Timestamp.from(occurredAt));
    }
}
