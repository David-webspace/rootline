package dev.rootline.messaging.consumer;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * outbox-relay 送出的事件信封。所有服務的消費端用同一個解析器，格式約定只有一份。
 *
 * @param payload 原事件的欄位（JSON 轉成 Map，避免消費端依賴發佈端的 Java 類別）
 */
public record EventEnvelope(UUID eventId, String eventType, String aggregateType, String aggregateId, String tenantId,
                            Instant occurredAt, Map<String, Object> payload) {

    @SuppressWarnings("unchecked")
    public static EventEnvelope parse(String message, JsonMapper json) {
        try {
            JsonNode node = json.readTree(message);
            String eventId = node.path("eventId").asString("");
            String eventType = node.path("eventType").asString("");
            String tenantId = node.path("tenantId").asString("");
            if (eventId.isBlank() || eventType.isBlank() || tenantId.isBlank() || !node.path("payload").isObject()) {
                throw new PoisonEventException("Malformed event envelope");
            }
            String occurredAt = node.path("occurredAt").asString("");
            return new EventEnvelope(UUID.fromString(eventId), eventType, node.path("aggregateType").asString(""),
                    node.path("aggregateId").asString(""), tenantId,
                    occurredAt.isBlank() ? Instant.EPOCH : Instant.parse(occurredAt),
                    json.convertValue(node.path("payload"), Map.class));
        } catch (JacksonException | IllegalArgumentException | DateTimeParseException e) {
            throw new PoisonEventException("Cannot parse event envelope: " + e.getMessage(), e);
        }
    }

    public String text(String field) {
        Object value = payload.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new PoisonEventException(eventType + " payload is missing '" + field + "'");
        }
        return s;
    }

    public String optionalText(String field, String fallback) {
        return payload.get(field) instanceof String s ? s : fallback;
    }

    public long number(String field) {
        if (!(payload.get(field) instanceof Number n)) {
            throw new PoisonEventException(eventType + " payload is missing numeric '" + field + "'");
        }
        return n.longValue();
    }
}
