package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.shared.TenantId;

/**
 * 讀取端投影（部門視圖 + 閉包表 + 人員對照）。與事件寫入在同一個 transaction 內更新，
 * 所以命令端做跨聚合檢查時讀到的一定是最新狀態。
 */
public interface DepartmentProjection {

    void apply(DepartmentEvent event);

    /** 清空某租戶的所有投影（重建前使用）。事件庫不受影響。 */
    void clear(TenantId tenantId);
}
