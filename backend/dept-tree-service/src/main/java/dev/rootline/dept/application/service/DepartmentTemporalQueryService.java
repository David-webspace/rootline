package dev.rootline.dept.application.service;

import dev.rootline.dept.application.domain.dept.Department;
import dev.rootline.dept.application.domain.dept.Department.Snapshot;
import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.dept.EmployeeId;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentEventStore;
import dev.rootline.dept.application.port.DepartmentSnapshotStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 時光機：任何部門在任何時間點的狀態都能從事件庫推回來。
 * 先找該時間點之前最近的快照，再重播快照之後、該時間點之前的事件。
 */
@Service
@Transactional(readOnly = true)
public class DepartmentTemporalQueryService {

    private final DepartmentEventStore store;
    private final DepartmentSnapshotStore snapshots;

    public DepartmentTemporalQueryService(DepartmentEventStore store, DepartmentSnapshotStore snapshots) {
        this.store = store;
        this.snapshots = snapshots;
    }

    public List<HistoryEntry> history(TenantId tenantId, String departmentId) {
        DepartmentId id = DepartmentId.of(departmentId);
        List<DepartmentEventStore.StoredEvent> events = store.history(tenantId, id);
        if (events.isEmpty()) {
            throw DomainException.notFound("DEPARTMENT_NOT_FOUND", "Department " + id + " not found");
        }
        return events.stream()
                .map(e -> new HistoryEntry(e.version(), e.event().eventType(), e.event().occurredAt(),
                        e.event().operator(), e.event()))
                .toList();
    }

    public State current(TenantId tenantId, String departmentId) {
        return stateAt(tenantId, departmentId, Instant.now());
    }

    public State stateAt(TenantId tenantId, String departmentId, Instant instant) {
        DepartmentId id = DepartmentId.of(departmentId);
        Optional<Snapshot> snapshot = snapshots.latestAtOrBefore(tenantId, id, instant);
        long fromVersion = snapshot.map(Snapshot::version).orElse(0L);
        List<DepartmentEvent> tail = store.loadUpTo(tenantId, id, fromVersion, instant);
        if (snapshot.isEmpty() && tail.isEmpty()) {
            throw DomainException.notFound("DEPARTMENT_NOT_YET_EXISTED",
                    "Department " + id + " did not exist at " + instant);
        }
        Department department = snapshot.isPresent()
                ? Department.fromSnapshot(snapshot.get(), tail)
                : Department.rehydrate(tail);
        return State.of(department, instant);
    }

    public record HistoryEntry(long version, String eventType, Instant occurredAt, String operator,
                               DepartmentEvent payload) {
    }

    public record State(Instant asOf, String id, String parentId, String code, String name, String status,
                        int sortOrder, boolean deleted, List<String> employees, long version,
                        Instant lastModifiedAt, String lastModifiedBy) {

        static State of(Department d, Instant asOf) {
            return new State(asOf, d.id().value(), d.parentId() == null ? null : d.parentId().value(),
                    d.code().value(), d.name(), d.status().name(), d.sortOrder(), d.isDeleted(),
                    d.employees().stream().map(EmployeeId::value).sorted().toList(), d.version(),
                    d.lastModifiedAt(), d.lastModifiedBy());
        }
    }
}
