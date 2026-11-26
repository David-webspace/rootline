package dev.rootline.dept.application.service;

import dev.rootline.dept.application.domain.dept.Department;
import dev.rootline.dept.application.domain.dept.Department.PendingChanges;
import dev.rootline.dept.application.domain.dept.Department.Snapshot;
import dev.rootline.dept.application.domain.dept.DepartmentCode;
import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.dept.DepartmentStatus;
import dev.rootline.dept.application.domain.dept.EmployeeId;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentEventStore;
import dev.rootline.dept.application.port.DepartmentProjection;
import dev.rootline.dept.application.port.DepartmentReadModel;
import dev.rootline.dept.application.port.DepartmentReadModel.Node;
import dev.rootline.dept.application.port.DepartmentSnapshotStore;
import dev.rootline.dept.application.port.DomainEventOutbox;
import dev.rootline.dept.application.port.TreeLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部門的寫入用例。每個用例在一個 transaction 內：讀事件 → 重建聚合 → 執行行為 →
 * 寫事件（樂觀鎖）→ 更新投影 → 寫 outbox。任何一步失敗全部 rollback。
 */
@Service
@Transactional
public class DepartmentCommandService {

    private final DepartmentEventStore store;
    private final DepartmentSnapshotStore snapshots;
    private final DepartmentProjection projection;
    private final DepartmentReadModel readModel;
    private final TreeLock lock;
    private final DomainEventOutbox outbox;
    private final int snapshotInterval;

    public DepartmentCommandService(DepartmentEventStore store, DepartmentSnapshotStore snapshots,
                                    DepartmentProjection projection, DepartmentReadModel readModel, TreeLock lock,
                                    DomainEventOutbox outbox,
                                    @Value("${rootline.dept.snapshot-interval:20}") int snapshotInterval) {
        this.store = store;
        this.snapshots = snapshots;
        this.projection = projection;
        this.readModel = readModel;
        this.lock = lock;
        this.outbox = outbox;
        this.snapshotInterval = Math.max(snapshotInterval, 1);
    }

    public String create(TenantId tenantId, String id, String parentId, String code, String name, String operator) {
        lock.lockTenant(tenantId);
        return createUnlocked(tenantId, id, parentId, code, name, operator);
    }

    /** 一次建立一整棵子樹；任何一個節點失敗（例如代碼重複）整棵都不會建立。 */
    public List<String> createTree(TenantId tenantId, String parentId, TreeSpec root, String operator) {
        lock.lockTenant(tenantId);
        List<String> created = new ArrayList<>();
        createRecursively(tenantId, parentId, root, operator, created);
        return created;
    }

    public void move(TenantId tenantId, String departmentId, String newParentId, String operator) {
        lock.lockTenant(tenantId);
        Department department = load(tenantId, departmentId);
        DepartmentId target = newParentId == null || newParentId.isBlank() ? null : DepartmentId.of(newParentId);
        if (target != null) {
            requireUsableParent(tenantId, target);
            if (readModel.isAncestorOrSelf(tenantId, department.id(), target)) {
                throw DomainException.violation("CYCLE_DETECTED",
                        "Cannot move " + department.code() + " under its own descendant");
            }
        }
        department.moveTo(target, operator);
        persist(department);
    }

    public void rename(TenantId tenantId, String departmentId, String newName, String operator) {
        Department department = load(tenantId, departmentId);
        department.rename(newName, operator);
        persist(department);
    }

    public void changeSortOrder(TenantId tenantId, String departmentId, int sortOrder, String operator) {
        Department department = load(tenantId, departmentId);
        department.changeSortOrder(sortOrder, operator);
        persist(department);
    }

    /** 連同整棵子樹一起停用；只要其中一個部門還有人，整個動作都不生效。 */
    public int disable(TenantId tenantId, String departmentId, String operator) {
        lock.lockTenant(tenantId);
        Department root = load(tenantId, departmentId);
        int disabled = 0;
        for (Node node : readModel.subtree(tenantId, root.id(), false, true)) {
            if (!DepartmentStatus.ACTIVE.name().equals(node.status())) {
                continue;
            }
            Department department = node.id().equals(root.id().value()) ? root : load(tenantId, node.id());
            department.disable(operator);
            persist(department);
            disabled++;
        }
        return disabled;
    }

    public void assignEmployee(TenantId tenantId, String departmentId, String employeeId, String operator) {
        Department department = load(tenantId, departmentId);
        department.assignEmployee(EmployeeId.of(employeeId), operator);
        persist(department);
    }

    public void unassignEmployee(TenantId tenantId, String departmentId, String employeeId, String operator) {
        Department department = load(tenantId, departmentId);
        department.unassignEmployee(EmployeeId.of(employeeId), operator);
        persist(department);
    }

    /**
     * 連同整棵子樹一起邏輯刪除。子樹裡只要還有人，整個動作都不生效。
     *
     * @return 實際被刪除的部門數
     */
    public int delete(TenantId tenantId, String departmentId, String operator) {
        lock.lockTenant(tenantId);
        Department root = load(tenantId, departmentId);
        if (root.isDeleted()) {
            throw DomainException.violation("DEPARTMENT_ALREADY_DELETED", "Department " + root.code() + " is already deleted");
        }
        List<Node> subtree = readModel.subtree(tenantId, root.id(), false, true);
        int deleted = 0;
        for (Node node : subtree) {
            Department department = node.id().equals(root.id().value()) ? root : load(tenantId, node.id());
            department.delete(operator);
            persist(department);
            deleted++;
        }
        return deleted;
    }

    /**
     * 「時光機復活」：復原部門以及它底下所有已刪除的子部門。
     * 若原本的上層已不存在（被刪除），改掛到最上層，避免產生看不到的孤兒子樹。
     *
     * @return 實際被復原的部門數
     */
    public RestoreResult restore(TenantId tenantId, String departmentId, String operator) {
        lock.lockTenant(tenantId);
        Department root = load(tenantId, departmentId);
        boolean parentAlive = root.parentId() == null || readModel.find(tenantId, root.parentId())
                .filter(parent -> !parent.deleted()).isPresent();
        root.restore(parentAlive, operator);
        persist(root);

        int restored = 1;
        for (Node node : readModel.subtree(tenantId, root.id(), true, true)) {
            if (node.deleted() && !node.id().equals(root.id().value())) {
                Department descendant = load(tenantId, node.id());
                descendant.restore(true, operator);
                persist(descendant);
                restored++;
            }
        }
        return new RestoreResult(restored, !parentAlive);
    }

    /**
     * 把 {@code sourceId} 併入 {@code targetId}：子部門改掛到目標底下、人員移到目標，
     * 最後來源標記為已合併（停用）。目標不能是來源本身或來源的子孫，否則子樹會接到自己身上。
     */
    public MergeResult merge(TenantId tenantId, String sourceId, String targetId, String operator) {
        lock.lockTenant(tenantId);
        Department source = load(tenantId, sourceId);
        DepartmentId target = DepartmentId.of(targetId);
        if (readModel.isAncestorOrSelf(tenantId, source.id(), target)) {
            throw DomainException.violation("MERGE_INTO_DESCENDANT",
                    "Cannot merge " + source.code() + " into itself or one of its descendants");
        }
        requireUsableParent(tenantId, target);
        Department targetDepartment = load(tenantId, targetId);

        int movedChildren = 0;
        for (Node child : readModel.children(tenantId, source.id())) {
            Department department = load(tenantId, child.id());
            department.moveTo(target, operator);
            persist(department);
            movedChildren++;
        }

        int movedEmployees = 0;
        for (EmployeeId employee : List.copyOf(source.employees())) {
            source.unassignEmployee(employee, operator);
            if (!targetDepartment.employees().contains(employee)) {
                targetDepartment.assignEmployee(employee, operator);
            }
            movedEmployees++;
        }
        persist(targetDepartment);

        source.markMergedInto(target, operator);
        persist(source);
        return new MergeResult(movedChildren, movedEmployees);
    }

    public record RestoreResult(int restored, boolean reattachedToRoot) {
    }

    public record MergeResult(int movedChildren, int movedEmployees) {
    }

    // ---------- 內部 ----------

    private void createRecursively(TenantId tenantId, String parentId, TreeSpec spec, String operator,
                                   List<String> created) {
        String id = createUnlocked(tenantId, spec.id(), parentId, spec.code(), spec.name(), operator);
        created.add(id);
        for (TreeSpec child : spec.children()) {
            createRecursively(tenantId, id, child, operator, created);
        }
    }

    private String createUnlocked(TenantId tenantId, String id, String parentId, String code, String name,
                                  String operator) {
        DepartmentId departmentId = id == null || id.isBlank() ? DepartmentId.generate() : DepartmentId.of(id);
        DepartmentCode departmentCode = DepartmentCode.of(code);
        if (readModel.find(tenantId, departmentId).isPresent()) {
            throw DomainException.conflict("DEPARTMENT_ID_TAKEN", "Department id " + departmentId + " already exists");
        }
        if (readModel.codeExists(tenantId, departmentCode)) {
            throw DomainException.conflict("DEPARTMENT_CODE_TAKEN", "Department code " + departmentCode + " already exists");
        }
        DepartmentId parent = parentId == null || parentId.isBlank() ? null : DepartmentId.of(parentId);
        if (parent != null) {
            requireUsableParent(tenantId, parent);
        }
        Department department = Department.create(tenantId, departmentId, parent, departmentCode, name, operator);
        persist(department);
        return departmentId.value();
    }

    private void requireUsableParent(TenantId tenantId, DepartmentId parentId) {
        Node parent = readModel.find(tenantId, parentId)
                .filter(n -> !n.deleted())
                .orElseThrow(() -> DomainException.notFound("PARENT_NOT_FOUND", "Parent " + parentId + " not found"));
        if (!DepartmentStatus.ACTIVE.name().equals(parent.status())) {
            throw DomainException.violation("PARENT_DISABLED", "Parent " + parent.code() + " is disabled");
        }
    }

    /** 有快照就從快照接續重播，否則從第一個事件開始。 */
    Department load(TenantId tenantId, String departmentId) {
        DepartmentId id = DepartmentId.of(departmentId);
        Optional<Snapshot> snapshot = snapshots.latest(tenantId, id);
        if (snapshot.isPresent()) {
            return Department.fromSnapshot(snapshot.get(), store.loadAfter(tenantId, id, snapshot.get().version()));
        }
        List<DepartmentEvent> history = store.load(tenantId, id);
        if (history.isEmpty()) {
            throw DomainException.notFound("DEPARTMENT_NOT_FOUND", "Department " + id + " not found");
        }
        return Department.rehydrate(history);
    }

    void persist(Department department) {
        PendingChanges changes = department.pullChanges();
        if (changes.isEmpty()) {
            return;
        }
        store.append(department.tenantId(), department.id(), changes.expectedVersion(), changes.events());
        changes.events().forEach(projection::apply);
        outbox.append(changes.events());
        if (changes.expectedVersion() / snapshotInterval != department.version() / snapshotInterval) {
            snapshots.save(department.snapshot());
        }
    }

    /** 批次建樹的一個節點。id 可省略（由伺服器產生）。 */
    public record TreeSpec(String id, String code, String name, List<TreeSpec> children) {

        public TreeSpec {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
