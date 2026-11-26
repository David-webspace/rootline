package dev.rootline.dept.iface.messaging;

import dev.rootline.dept.application.service.TenantProvisioningService;
import dev.rootline.dept.application.service.TenantProvisioningService.TenantEvent;
import dev.rootline.messaging.consumer.EventEnvelope;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class TenantLifecycleListener {

    private final TenantProvisioningService provisioning;
    private final JsonMapper json;

    TenantLifecycleListener(TenantProvisioningService provisioning, JsonMapper json) {
        this.provisioning = provisioning;
        this.json = json;
    }

    @KafkaListener(topics = "rootline.tenant.lifecycle", groupId = TenantProvisioningService.CONSUMER)
    void onTenantEvent(String message) {
        EventEnvelope e = EventEnvelope.parse(message, json);
        provisioning.apply(new TenantEvent(e.eventId(), e.eventType(), e.tenantId(), e.payload()));
    }
}
