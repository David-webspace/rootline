package dev.rootline.dept.application.service;

import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentEventStore;
import dev.rootline.dept.application.port.DepartmentProjection;
import dev.rootline.dept.application.port.DepartmentReadModel;
import dev.rootline.dept.application.port.TreeLock;
import dev.rootline.dept.application.service.DepartmentViews.RebuildResult;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清空某租戶的投影，從事件庫依寫入順序全部重播。用途：投影邏輯修改後補算、投影資料毀損時修復。
 * 整段在一個 transaction 內並持有租戶鎖，重建期間的查詢仍看到舊投影，不會讀到半套。
 */
@Service
public class ProjectionRebuildService {

    private final DepartmentEventStore store;
    private final DepartmentProjection projection;
    private final DepartmentReadModel readModel;
    private final TreeLock lock;

    public ProjectionRebuildService(DepartmentEventStore store, DepartmentProjection projection,
                                    DepartmentReadModel readModel, TreeLock lock) {
        this.store = store;
        this.projection = projection;
        this.readModel = readModel;
        this.lock = lock;
    }

    @Transactional
    public RebuildResult rebuild(TenantId tenantId) {
        lock.lockTenant(tenantId);
        projection.clear(tenantId);
        AtomicLong replayed = new AtomicLong();
        store.forEachInTenant(tenantId, event -> {
            projection.apply(event);
            replayed.incrementAndGet();
        });
        var counts = readModel.statistics(tenantId);
        return new RebuildResult(replayed.get(), counts.departments(), counts.closureRows());
    }
}
