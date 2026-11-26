package dev.rootline.auth.application.domain.user;

import dev.rootline.auth.application.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** User 聚合產生的事件。發佈到 {@code rootline.auth.user}。 */
public sealed interface UserEvent extends DomainEvent {

    String userId();

    String username();

    @Override
    default String aggregateType() {
        return "User";
    }

    @Override
    default String aggregateId() {
        return userId();
    }

    record UserRegistered(UUID eventId, Instant occurredAt, String tenantId, String userId, String username,
                          String email) implements UserEvent {
    }

    record UserEmailChanged(UUID eventId, Instant occurredAt, String tenantId, String userId, String username,
                            String email) implements UserEvent {
    }

    record UserPasswordChanged(UUID eventId, Instant occurredAt, String tenantId, String userId,
                               String username) implements UserEvent {
    }

    record UserLocked(UUID eventId, Instant occurredAt, String tenantId, String userId, String username,
                      int failedAttempts) implements UserEvent {
    }

    record UserUnlocked(UUID eventId, Instant occurredAt, String tenantId, String userId,
                        String username) implements UserEvent {
    }

    record UserDeactivated(UUID eventId, Instant occurredAt, String tenantId, String userId,
                           String username) implements UserEvent {
    }

    record UserReactivated(UUID eventId, Instant occurredAt, String tenantId, String userId,
                           String username) implements UserEvent {
    }

    record UserRoleAssigned(UUID eventId, Instant occurredAt, String tenantId, String userId, String username,
                            String roleCode) implements UserEvent {
    }

    record UserRoleRevoked(UUID eventId, Instant occurredAt, String tenantId, String userId, String username,
                           String roleCode) implements UserEvent {
    }
}
