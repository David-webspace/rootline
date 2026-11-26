package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.dept.Department.Snapshot;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.time.Instant;
import java.util.Optional;

/**
 * 聚合快照。快照只是效能最佳化：刪掉全部快照，系統行為完全不變（只是載入變慢）。
 */
public interface DepartmentSnapshotStore {

    Optional<Snapshot> latest(TenantId tenantId, DepartmentId departmentId);

    /** 最後一個事件發生時間不晚於 {@code instant} 的最新快照。 */
    Optional<Snapshot> latestAtOrBefore(TenantId tenantId, DepartmentId departmentId, Instant instant);

    void save(Snapshot snapshot);
}
