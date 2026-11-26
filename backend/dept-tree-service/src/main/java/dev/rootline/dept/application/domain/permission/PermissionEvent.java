package dev.rootline.dept.application.domain.permission;

import java.time.Instant;
import java.util.UUID;

/**
 * 權限定義的事件，發佈到 {@code rootline.dept.permission} 給 auth-service 投影成權限字典。
 * {@code version} 是邏輯時鐘：消費端只接受比手上更新的版本，重送或亂序到達的舊事件直接丟棄。
 */
public sealed interface PermissionEvent {

    UUID eventId();

    Instant occurredAt();

    String tenantId();

    String systemCode();

    String permissionCode();

    String name();

    String description();

    long version();

    String operator();

    default String eventType() {
        return getClass().getSimpleName();
    }

    default String aggregateId() {
        return systemCode() + ":" + permissionCode();
    }

    record PermissionDefined(UUID eventId, Instant occurredAt, String tenantId, String systemCode,
                             String permissionCode, String name, String description, long version,
                             String operator) implements PermissionEvent {
    }

    record PermissionUpdated(UUID eventId, Instant occurredAt, String tenantId, String systemCode,
                             String permissionCode, String name, String description, long version,
                             String operator) implements PermissionEvent {
    }
}
