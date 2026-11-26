package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.permission.PermissionDefinition;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.DepartmentDirectory;
import dev.rootline.auth.application.port.PermissionDictionary;
import dev.rootline.auth.application.port.ProcessedEvents;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 把 dept-tree-service 的事件投影到 auth 的本地資料。兩道防線：
 * <ol>
 *   <li>eventId 冪等：同一事件重送只處理一次</li>
 *   <li>權限定義的 version 邏輯時鐘：亂序到達的舊版本不會蓋掉新版本</li>
 * </ol>
 */
@Service
public class DeptEventProjectionService {

    public static final String PERMISSION_CONSUMER = "auth-permission-projector";
    public static final String DEPARTMENT_CONSUMER = "auth-department-projector";

    private static final Logger log = LoggerFactory.getLogger(DeptEventProjectionService.class);

    private final ProcessedEvents processed;
    private final PermissionDictionary dictionary;
    private final DepartmentDirectory directory;

    public DeptEventProjectionService(ProcessedEvents processed, PermissionDictionary dictionary,
                                      DepartmentDirectory directory) {
        this.processed = processed;
        this.dictionary = dictionary;
        this.directory = directory;
    }

    @Transactional
    public Outcome applyPermissionEvent(IncomingEvent event) {
        if (!processed.markProcessed(event.eventId(), PERMISSION_CONSUMER)) {
            return Outcome.DUPLICATE;
        }
        if (!event.eventType().equals("PermissionDefined") && !event.eventType().equals("PermissionUpdated")) {
            return Outcome.IGNORED;
        }
        Map<String, Object> p = event.payload();
        PermissionDefinition definition = new PermissionDefinition(TenantId.of(event.tenantId()),
                new PermissionKey(text(p, "systemCode"), text(p, "permissionCode")), text(p, "name"),
                (String) p.getOrDefault("description", ""), number(p, "version"));
        if (dictionary.upsertIfNewer(definition)) {
            return Outcome.APPLIED;
        }
        log.info("Ignored stale permission event {} {} v{}", event.eventId(), definition.key(), definition.version());
        return Outcome.STALE;
    }

    @Transactional
    public Outcome applyDepartmentEvent(IncomingEvent event) {
        if (!processed.markProcessed(event.eventId(), DEPARTMENT_CONSUMER)) {
            return Outcome.DUPLICATE;
        }
        TenantId tenant = TenantId.of(event.tenantId());
        Map<String, Object> p = event.payload();
        String departmentId = text(p, "departmentId");
        switch (event.eventType()) {
            case "DepartmentCreated" -> directory.upsertDepartment(tenant, departmentId, text(p, "name"));
            case "DepartmentRenamed" -> directory.upsertDepartment(tenant, departmentId, text(p, "newName"));
            case "DepartmentDeleted" -> directory.markDeleted(tenant, departmentId, true);
            case "DepartmentRestored" -> directory.markDeleted(tenant, departmentId, false);
            case "EmployeeAssigned" -> directory.assign(tenant, text(p, "employeeId"), departmentId);
            case "EmployeeUnassigned" -> directory.unassign(tenant, text(p, "employeeId"), departmentId);
            default -> {
                return Outcome.IGNORED;
            }
        }
        return Outcome.APPLIED;
    }

    private static String text(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Event payload is missing '" + field + "'");
        }
        return s;
    }

    private static long number(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof Number n)) {
            throw new IllegalArgumentException("Event payload is missing numeric '" + field + "'");
        }
        return n.longValue();
    }

    /** 消費端看到的事件信封（與 outbox-relay 送出的格式一致）。 */
    public record IncomingEvent(UUID eventId, String eventType, String tenantId, Map<String, Object> payload) {
    }

    public enum Outcome {
        APPLIED, DUPLICATE, STALE, IGNORED
    }
}
