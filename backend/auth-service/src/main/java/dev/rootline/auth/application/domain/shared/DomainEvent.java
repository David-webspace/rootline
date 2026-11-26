package dev.rootline.auth.application.domain.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * 所有領域事件的共同介面。事件只放字串與基本型別，
 * 方便 outbox 直接序列化，也不讓事件契約綁死在 value object 上。
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    String tenantId();

    String aggregateType();

    String aggregateId();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
