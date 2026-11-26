package dev.rootline.auth.iface.messaging;

import dev.rootline.auth.application.service.DeptEventProjectionService;
import dev.rootline.auth.application.service.DeptEventProjectionService.IncomingEvent;
import dev.rootline.auth.application.service.DeptEventProjectionService.Outcome;
import dev.rootline.messaging.consumer.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 訂閱 dept-tree-service 的事件。這裡只負責拆信封，業務邏輯在 application service。 */
@Component
class DeptEventListener {

    private static final Logger log = LoggerFactory.getLogger(DeptEventListener.class);

    private final DeptEventProjectionService projection;
    private final JsonMapper json;

    DeptEventListener(DeptEventProjectionService projection, JsonMapper json) {
        this.projection = projection;
        this.json = json;
    }

    @KafkaListener(topics = "rootline.dept.permission", groupId = DeptEventProjectionService.PERMISSION_CONSUMER)
    void onPermissionEvent(String message) {
        Outcome outcome = projection.applyPermissionEvent(parse(message));
        log.debug("Permission event processed: {}", outcome);
    }

    @KafkaListener(topics = "rootline.dept.department", groupId = DeptEventProjectionService.DEPARTMENT_CONSUMER)
    void onDepartmentEvent(String message) {
        Outcome outcome = projection.applyDepartmentEvent(parse(message));
        log.debug("Department event processed: {}", outcome);
    }

    private IncomingEvent parse(String message) {
        EventEnvelope envelope = EventEnvelope.parse(message, json);
        return new IncomingEvent(envelope.eventId(), envelope.eventType(), envelope.tenantId(), envelope.payload());
    }
}
