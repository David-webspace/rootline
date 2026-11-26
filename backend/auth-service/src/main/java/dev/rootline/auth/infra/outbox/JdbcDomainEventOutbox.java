package dev.rootline.auth.infra.outbox;

import dev.rootline.auth.application.domain.shared.DomainEvent;
import dev.rootline.auth.application.port.DomainEventOutbox;
import java.util.Collection;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 把事件寫進 outbox_events。{@code MANDATORY} 確保一定跑在業務 transaction 裡，
 * 不會出現「資料存了、事件沒存」或反過來的情況。推到 Kafka 的 poller 在 M4 實作。
 */
@Component
class JdbcDomainEventOutbox implements DomainEventOutbox {

    static final Map<String, String> TOPICS = Map.of(
            "User", "rootline.auth.user",
            "Role", "rootline.auth.role",
            "Group", "rootline.auth.group");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    JdbcDomainEventOutbox(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Collection<? extends DomainEvent> events) {
        for (DomainEvent event : events) {
            String topic = TOPICS.get(event.aggregateType());
            if (topic == null) {
                throw new IllegalStateException("No topic mapped for aggregate " + event.aggregateType());
            }
            jdbc.update("""
                    INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                    """,
                    event.eventId(), event.aggregateType(), event.aggregateId(), event.eventType(), topic,
                    json.writeValueAsString(event), java.sql.Timestamp.from(event.occurredAt()));
        }
    }
}
