package dev.rootline.dept.infra.messaging;

import dev.rootline.dept.application.port.ProcessedEvents;
import dev.rootline.messaging.consumer.ProcessedEventGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class GuardedProcessedEvents implements ProcessedEvents {

    private final ProcessedEventGuard guard;

    GuardedProcessedEvents(ProcessedEventGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean markProcessed(UUID eventId, String consumer) {
        return guard.firstTime(eventId, consumer);
    }
}
