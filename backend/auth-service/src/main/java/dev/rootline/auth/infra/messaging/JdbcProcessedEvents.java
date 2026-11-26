package dev.rootline.auth.infra.messaging;

import dev.rootline.auth.application.port.ProcessedEvents;
import dev.rootline.messaging.consumer.ProcessedEventGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** application 的冪等 port，委派給 shared-messaging 的共用實作。 */
@Component
class JdbcProcessedEvents implements ProcessedEvents {

    private final ProcessedEventGuard guard;

    JdbcProcessedEvents(ProcessedEventGuard guard) {
        this.guard = guard;
    }

    @Override
    public boolean markProcessed(UUID eventId, String consumer) {
        return guard.firstTime(eventId, consumer);
    }
}
