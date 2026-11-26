package dev.rootline.auth.application.domain.role;

import dev.rootline.auth.application.domain.shared.DomainEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Role 聚合產生的事件。發佈到 {@code rootline.auth.role}。 */
public sealed interface RoleEvent extends DomainEvent {

    String roleId();

    String roleCode();

    @Override
    default String aggregateType() {
        return "Role";
    }

    @Override
    default String aggregateId() {
        return roleId();
    }

    record RoleCreated(UUID eventId, Instant occurredAt, String tenantId, String roleId, String roleCode,
                       String roleName, boolean systemRoot, List<String> permissions) implements RoleEvent {
    }

    record RoleRenamed(UUID eventId, Instant occurredAt, String tenantId, String roleId, String roleCode,
                       String roleName) implements RoleEvent {
    }

    record RolePermissionGranted(UUID eventId, Instant occurredAt, String tenantId, String roleId, String roleCode,
                                 String permission) implements RoleEvent {
    }

    record RolePermissionRevoked(UUID eventId, Instant occurredAt, String tenantId, String roleId, String roleCode,
                                 String permission) implements RoleEvent {
    }
}
