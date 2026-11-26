package dev.rootline.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Transactional Outbox 的送出端。
 * <ul>
 *   <li>{@code FOR UPDATE SKIP LOCKED}：多個服務實例同時跑也不會重複送同一筆，彼此自動分工，
 *       不需要額外的分散式鎖</li>
 *   <li>依 {@code seq} 順序送出，遇到失敗就停在那一筆（不跳過），維持同一聚合的事件順序</li>
 *   <li>Kafka message key = aggregate id，同一聚合的事件進同一個 partition，消費端看到的順序與寫入一致</li>
 *   <li>保證「至少一次」：送出成功但標記 PUBLISHED 前當掉，下次會重送，所以消費端必須冪等</li>
 * </ul>
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;
    private final OutboxRelayProperties properties;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
                       JsonMapper json, OutboxRelayProperties properties) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.json = json;
        this.properties = properties;
    }

    /** @return 這一輪成功送出的筆數 */
    public int relayBatch() {
        Integer sent = tx.execute(status -> {
            List<Row> rows = jdbc.query("""
                    SELECT id, aggregate_type, aggregate_id, event_type, topic, payload::text AS payload, created_at
                    FROM outbox_events
                    WHERE status = 'PENDING'
                    ORDER BY seq
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """, (rs, i) -> new Row((UUID) rs.getObject("id"), rs.getString("aggregate_type"),
                    rs.getString("aggregate_id"), rs.getString("event_type"), rs.getString("topic"),
                    rs.getString("payload"), rs.getTimestamp("created_at").toInstant()), properties.batchSize());

            int published = 0;
            for (Row row : rows) {
                try {
                    send(row);
                    jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published_at = now() WHERE id = ?",
                            row.id());
                    published++;
                } catch (Exception e) {
                    recordFailure(row, e);
                    break;
                }
            }
            return published;
        });
        return sent == null ? 0 : sent;
    }

    /** 清掉超過保留期限的已送出事件。 */
    public int purgePublished() {
        return jdbc.update("DELETE FROM outbox_events WHERE status = 'PUBLISHED' AND published_at < ?",
                Timestamp.from(Instant.now().minus(properties.retention())));
    }

    private void send(Row row) throws Exception {
        JsonNode payload = json.readTree(row.payload());
        ObjectNode envelope = json.createObjectNode();
        envelope.put("eventId", row.id().toString());
        envelope.put("eventType", row.eventType());
        envelope.put("aggregateType", row.aggregateType());
        envelope.put("aggregateId", row.aggregateId());
        envelope.put("tenantId", payload.path("tenantId").asString(""));
        envelope.put("occurredAt", row.createdAt().toString());
        envelope.set("payload", payload);

        ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.aggregateId(),
                json.writeValueAsString(envelope));
        record.headers().add("eventType", row.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", row.id().toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void recordFailure(Row row, Exception e) {
        int attempts = jdbc.queryForObject("""
                UPDATE outbox_events
                SET retry_count = retry_count + 1,
                    status = CASE WHEN retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END
                WHERE id = ? RETURNING retry_count
                """, Integer.class, properties.maxAttempts(), row.id());
        if (attempts >= properties.maxAttempts()) {
            log.error("Outbox event {} ({}) marked FAILED after {} attempts: {}", row.id(), row.eventType(), attempts,
                    e.toString());
        } else {
            log.warn("Outbox event {} ({}) failed to publish (attempt {}): {}", row.id(), row.eventType(), attempts,
                    e.toString());
        }
    }

    private record Row(UUID id, String aggregateType, String aggregateId, String eventType, String topic,
                       String payload, Instant createdAt) {
    }
}
