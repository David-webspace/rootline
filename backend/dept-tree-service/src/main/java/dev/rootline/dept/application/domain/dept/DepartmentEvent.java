package dev.rootline.dept.application.domain.dept;

import java.time.Instant;
import java.util.UUID;

/**
 * Department 聚合的所有事件。這是 Event Sourcing 的唯一真相：
 * 聚合狀態、讀取端投影、時光機查詢都從這串事件推導出來。
 * 每個事件都記錄 operator，事件流本身就是完整的稽核軌跡。
 */
public sealed interface DepartmentEvent {

    UUID eventId();

    Instant occurredAt();

    String tenantId();

    String departmentId();

    String operator();

    default String eventType() {
        return getClass().getSimpleName();
    }

    record DepartmentCreated(UUID eventId, Instant occurredAt, String tenantId, String departmentId, String operator,
                             String parentId, String code, String name, int sortOrder) implements DepartmentEvent {
    }

    record DepartmentRenamed(UUID eventId, Instant occurredAt, String tenantId, String departmentId, String operator,
                             String oldName, String newName) implements DepartmentEvent {
    }

    record DepartmentMoved(UUID eventId, Instant occurredAt, String tenantId, String departmentId, String operator,
                           String oldParentId, String newParentId) implements DepartmentEvent {
    }

    record DepartmentSortOrderChanged(UUID eventId, Instant occurredAt, String tenantId, String departmentId,
                                      String operator, int sortOrder) implements DepartmentEvent {
    }

    record DepartmentDisabled(UUID eventId, Instant occurredAt, String tenantId, String departmentId,
                              String operator) implements DepartmentEvent {
    }

    record EmployeeAssigned(UUID eventId, Instant occurredAt, String tenantId, String departmentId, String operator,
                            String employeeId) implements DepartmentEvent {
    }

    record EmployeeUnassigned(UUID eventId, Instant occurredAt, String tenantId, String departmentId, String operator,
                              String employeeId) implements DepartmentEvent {
    }

    record DepartmentDeleted(UUID eventId, Instant occurredAt, String tenantId, String departmentId,
                             String operator) implements DepartmentEvent {
    }

    /** 復原；若原上層已不存在，{@code reattachedToRoot} 為 true，同時 parentId 變成 null。 */
    record DepartmentRestored(UUID eventId, Instant occurredAt, String tenantId, String departmentId, String operator,
                              boolean reattachedToRoot) implements DepartmentEvent {
    }

    record DepartmentMerged(UUID eventId, Instant occurredAt, String tenantId, String departmentId, String operator,
                            String targetDepartmentId) implements DepartmentEvent {
    }
}
