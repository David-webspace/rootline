package dev.rootline.auth.application.domain.group;

import dev.rootline.auth.application.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Group 聚合產生的事件。發佈到 {@code rootline.auth.group}。 */
public sealed interface GroupEvent extends DomainEvent {

    String groupId();

    String groupCode();

    @Override
    default String aggregateType() {
        return "Group";
    }

    @Override
    default String aggregateId() {
        return groupId();
    }

    record GroupCreated(UUID eventId, Instant occurredAt, String tenantId, String groupId, String groupCode,
                        String groupName) implements GroupEvent {
    }

    record GroupRenamed(UUID eventId, Instant occurredAt, String tenantId, String groupId, String groupCode,
                        String groupName) implements GroupEvent {
    }

    record GroupMemberAdded(UUID eventId, Instant occurredAt, String tenantId, String groupId, String groupCode,
                            String username) implements GroupEvent {
    }

    record GroupMemberRemoved(UUID eventId, Instant occurredAt, String tenantId, String groupId, String groupCode,
                              String username) implements GroupEvent {
    }

    record GroupRoleAssigned(UUID eventId, Instant occurredAt, String tenantId, String groupId, String groupCode,
                             String roleCode) implements GroupEvent {
    }

    record GroupRoleRevoked(UUID eventId, Instant occurredAt, String tenantId, String groupId, String groupCode,
                            String roleCode) implements GroupEvent {
    }
}
