package dev.rootline.dept.application.domain.dept;

import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentCreated;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentDeleted;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentDisabled;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentMerged;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentMoved;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentRenamed;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentRestored;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.DepartmentSortOrderChanged;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.EmployeeAssigned;
import dev.rootline.dept.application.domain.dept.DepartmentEvent.EmployeeUnassigned;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 部門聚合根（Event Sourced）。
 * <p>
 * 狀態只能透過事件改變：行為方法先檢查不變量，再 {@link #raise} 事件；
 * {@link #apply} 是唯一修改欄位的地方，從歷史重建（{@link #rehydrate}）也走同一段程式，
 * 所以「現在的狀態」與「重播出來的狀態」不可能不一致。
 * <p>
 * 跨聚合的規則（上層是否存在且啟用、代碼是否重複、搬移是否成環）需要讀其他部門，
 * 由 application service 查讀取端後再呼叫這裡；聚合只守住自己能判斷的規則。
 */
public final class Department {

    private final List<DepartmentEvent> pending = new ArrayList<>();

    private TenantId tenantId;
    private DepartmentId id;
    private DepartmentId parentId;
    private DepartmentCode code;
    private String name;
    private DepartmentStatus status;
    private int sortOrder;
    private boolean deleted;
    private final Set<EmployeeId> employees = new LinkedHashSet<>();
    /** 已套用的事件數，也就是下一個事件的版本號減一。 */
    private long version;
    private Instant lastModifiedAt;
    private String lastModifiedBy;

    private Department() {
    }

    // ---------- 建立與重建 ----------

    public static Department create(TenantId tenantId, DepartmentId id, DepartmentId parentId, DepartmentCode code,
                                    String name, String operator) {
        Department department = new Department();
        department.raise(new DepartmentCreated(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), parentId == null ? null : parentId.value(), code.value(),
                validName(name), 0));
        return department;
    }

    public static Department rehydrate(List<? extends DepartmentEvent> history) {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("Cannot rehydrate a department without events");
        }
        Department department = new Department();
        history.forEach(department::apply);
        return department;
    }

    // ---------- 行為 ----------

    public void rename(String newName, String operator) {
        requireAlive();
        requireActive("rename");
        String normalized = validName(newName);
        if (normalized.equals(name)) {
            return;
        }
        raise(new DepartmentRenamed(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), name, normalized));
    }

    /** @param newParentId null 代表搬到最上層成為 root */
    public void moveTo(DepartmentId newParentId, String operator) {
        requireAlive();
        requireActive("move");
        if (id.equals(newParentId)) {
            throw DomainException.violation("CYCLE_DETECTED", "A department cannot be its own parent");
        }
        if (Objects.equals(parentId, newParentId)) {
            return;
        }
        raise(new DepartmentMoved(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), valueOf(parentId), valueOf(newParentId)));
    }

    public void changeSortOrder(int newSortOrder, String operator) {
        requireAlive();
        if (newSortOrder < 0) {
            throw DomainException.invalid("SORT_ORDER_INVALID", "Sort order must be zero or positive");
        }
        if (newSortOrder == sortOrder) {
            return;
        }
        raise(new DepartmentSortOrderChanged(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), newSortOrder));
    }

    /** 還有直屬人員就不能停用，避免人員掛在一個不可用的部門底下。 */
    public void disable(String operator) {
        requireAlive();
        if (status == DepartmentStatus.DISABLED) {
            return;
        }
        if (!employees.isEmpty()) {
            throw DomainException.violation("DEPARTMENT_HAS_EMPLOYEES",
                    "Department " + code + " still has " + employees.size() + " employee(s)");
        }
        raise(new DepartmentDisabled(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator)));
    }

    public void assignEmployee(EmployeeId employee, String operator) {
        requireAlive();
        requireActive("assign employees to");
        if (employees.contains(employee)) {
            throw DomainException.conflict("EMPLOYEE_ALREADY_ASSIGNED", employee + " is already in " + code);
        }
        raise(new EmployeeAssigned(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), employee.value()));
    }

    public void unassignEmployee(EmployeeId employee, String operator) {
        requireAlive();
        if (!employees.contains(employee)) {
            throw DomainException.notFound("EMPLOYEE_NOT_ASSIGNED", employee + " is not in " + code);
        }
        raise(new EmployeeUnassigned(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), employee.value()));
    }

    /** 邏輯刪除。root 部門不能刪：每個租戶的組織樹至少要有根。子樹的連帶刪除由 application service 處理。 */
    public void delete(String operator) {
        if (deleted) {
            throw DomainException.violation("DEPARTMENT_ALREADY_DELETED", "Department " + code + " is already deleted");
        }
        if (parentId == null) {
            throw DomainException.violation("ROOT_NOT_DELETABLE", "A root department cannot be deleted");
        }
        if (!employees.isEmpty()) {
            throw DomainException.violation("DEPARTMENT_HAS_EMPLOYEES",
                    "Move the " + employees.size() + " employee(s) out of " + code + " before deleting it");
        }
        raise(new DepartmentDeleted(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator)));
    }

    /**
     * @param parentAlive 原上層目前是否仍存在且未刪除；若否，復原時改掛到最上層，避免產生孤兒節點
     */
    public void restore(boolean parentAlive, String operator) {
        if (!deleted) {
            throw DomainException.violation("DEPARTMENT_NOT_DELETED", "Department " + code + " is not deleted");
        }
        boolean reattach = parentId != null && !parentAlive;
        raise(new DepartmentRestored(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), reattach));
    }

    /** 合併的收尾：子部門與人員都已移走後，把自己標成停用並記錄併入對象。 */
    public void markMergedInto(DepartmentId target, String operator) {
        requireAlive();
        requireActive("merge");
        if (!employees.isEmpty()) {
            throw DomainException.violation("DEPARTMENT_HAS_EMPLOYEES", "Move employees out before merging " + code);
        }
        if (id.equals(target)) {
            throw DomainException.violation("MERGE_INTO_SELF", "A department cannot be merged into itself");
        }
        raise(new DepartmentMerged(UUID.randomUUID(), Instant.now(), tenantId.value(), id.value(),
                requireOperator(operator), target.value()));
    }

    // ---------- 事件套用（唯一修改狀態的地方）----------

    private void raise(DepartmentEvent event) {
        apply(event);
        pending.add(event);
    }

    private void apply(DepartmentEvent event) {
        switch (event) {
            case DepartmentCreated e -> {
                tenantId = TenantId.of(e.tenantId());
                id = DepartmentId.of(e.departmentId());
                parentId = e.parentId() == null ? null : DepartmentId.of(e.parentId());
                code = DepartmentCode.of(e.code());
                name = e.name();
                sortOrder = e.sortOrder();
                status = DepartmentStatus.ACTIVE;
            }
            case DepartmentRenamed e -> name = e.newName();
            case DepartmentMoved e -> parentId = e.newParentId() == null ? null : DepartmentId.of(e.newParentId());
            case DepartmentSortOrderChanged e -> sortOrder = e.sortOrder();
            case DepartmentDisabled e -> status = DepartmentStatus.DISABLED;
            case EmployeeAssigned e -> employees.add(EmployeeId.of(e.employeeId()));
            case EmployeeUnassigned e -> employees.remove(EmployeeId.of(e.employeeId()));
            case DepartmentDeleted e -> deleted = true;
            case DepartmentRestored e -> {
                deleted = false;
                if (e.reattachedToRoot()) {
                    parentId = null;
                }
            }
            case DepartmentMerged e -> status = DepartmentStatus.DISABLED;
        }
        version++;
        lastModifiedAt = event.occurredAt();
        lastModifiedBy = event.operator();
    }

    // ---------- 快照：事件很多時，從最近的快照開始重播即可 ----------

    public Snapshot snapshot() {
        return new Snapshot(tenantId.value(), id.value(), parentId == null ? null : parentId.value(), code.value(),
                name, status.name(), sortOrder, deleted, employees.stream().map(EmployeeId::value).toList(), version,
                lastModifiedAt, lastModifiedBy);
    }

    /** 從快照還原，再套用快照之後的事件。 */
    public static Department fromSnapshot(Snapshot snapshot, List<? extends DepartmentEvent> tail) {
        Department department = new Department();
        department.tenantId = TenantId.of(snapshot.tenantId());
        department.id = DepartmentId.of(snapshot.id());
        department.parentId = snapshot.parentId() == null ? null : DepartmentId.of(snapshot.parentId());
        department.code = DepartmentCode.of(snapshot.code());
        department.name = snapshot.name();
        department.status = DepartmentStatus.valueOf(snapshot.status());
        department.sortOrder = snapshot.sortOrder();
        department.deleted = snapshot.deleted();
        snapshot.employees().forEach(e -> department.employees.add(EmployeeId.of(e)));
        department.version = snapshot.version();
        department.lastModifiedAt = snapshot.lastModifiedAt();
        department.lastModifiedBy = snapshot.lastModifiedBy();
        tail.forEach(department::apply);
        return department;
    }

    /** 某個版本當下的完整狀態。只有基本型別，可直接序列化存進快照表。 */
    public record Snapshot(String tenantId, String id, String parentId, String code, String name, String status,
                           int sortOrder, boolean deleted, List<String> employees, long version,
                           Instant lastModifiedAt, String lastModifiedBy) {
    }

    // ---------- 工具 ----------

    /**
     * 取出待寫入的新事件，連同寫入前事件庫應有的版本（樂觀鎖用）。
     * 兩者一起取，避免呼叫端先清空事件再算版本而拿到錯的值。
     */
    public PendingChanges pullChanges() {
        PendingChanges changes = new PendingChanges(version - pending.size(), List.copyOf(pending));
        pending.clear();
        return changes;
    }

    public record PendingChanges(long expectedVersion, List<DepartmentEvent> events) {

        public boolean isEmpty() {
            return events.isEmpty();
        }
    }

    private void requireAlive() {
        if (deleted) {
            throw DomainException.violation("DEPARTMENT_DELETED", "Department " + code + " is deleted");
        }
    }

    private void requireActive(String action) {
        if (status == DepartmentStatus.DISABLED) {
            throw DomainException.violation("DEPARTMENT_DISABLED", "Cannot " + action + " disabled department " + code);
        }
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw DomainException.invalid("DEPARTMENT_NAME_INVALID", "Department name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw DomainException.invalid("DEPARTMENT_NAME_INVALID", "Department name must be at most 200 chars");
        }
        return trimmed;
    }

    private static String requireOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            throw DomainException.invalid("OPERATOR_REQUIRED", "Operator is required for the audit trail");
        }
        return operator;
    }

    private static String valueOf(DepartmentId id) {
        return id == null ? null : id.value();
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public DepartmentId id() {
        return id;
    }

    public DepartmentId parentId() {
        return parentId;
    }

    public DepartmentCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public DepartmentStatus status() {
        return status;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Set<EmployeeId> employees() {
        return Collections.unmodifiableSet(employees);
    }

    public long version() {
        return version;
    }

    public Instant lastModifiedAt() {
        return lastModifiedAt;
    }

    public String lastModifiedBy() {
        return lastModifiedBy;
    }
}
