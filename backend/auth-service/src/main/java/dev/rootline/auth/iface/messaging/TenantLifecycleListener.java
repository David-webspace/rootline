package dev.rootline.auth.iface.messaging;

import dev.rootline.auth.application.service.DeptEventProjectionService.IncomingEvent;
import dev.rootline.auth.application.service.TenantLifecycleService;
import dev.rootline.messaging.consumer.EventEnvelope;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class TenantLifecycleListener {

    private final TenantLifecycleService lifecycle;
    private final JsonMapper json;

    TenantLifecycleListener(TenantLifecycleService lifecycle, JsonMapper json) {
        this.lifecycle = lifecycle;
        this.json = json;
    }

    @KafkaListener(topics = "rootline.tenant.lifecycle", groupId = TenantLifecycleService.CONSUMER)
    void onTenantEvent(String message) {
        EventEnvelope envelope = EventEnvelope.parse(message, json);
        lifecycle.apply(new IncomingEvent(envelope.eventId(), envelope.eventType(), envelope.tenantId(),
                envelope.payload()));
    }
}
