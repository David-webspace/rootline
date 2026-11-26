package dev.rootline.userprofile.iface.messaging;

import dev.rootline.messaging.consumer.EventEnvelope;
import dev.rootline.userprofile.application.service.ProfileService;
import dev.rootline.userprofile.application.service.ProfileService.IncomingEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ProfileEventListener {

    private final ProfileService profiles;
    private final JsonMapper json;

    ProfileEventListener(ProfileService profiles, JsonMapper json) {
        this.profiles = profiles;
        this.json = json;
    }

    @KafkaListener(topics = "rootline.auth.user", groupId = ProfileService.USER_CONSUMER)
    void onUserEvent(String message) {
        profiles.applyUserEvent(parse(message));
    }

    @KafkaListener(topics = "rootline.kyc.status", groupId = ProfileService.KYC_CONSUMER)
    void onKycEvent(String message) {
        profiles.applyKycEvent(parse(message));
    }

    private IncomingEvent parse(String message) {
        EventEnvelope e = EventEnvelope.parse(message, json);
        return new IncomingEvent(e.eventId(), e.eventType(), e.tenantId(), e.payload());
    }
}
