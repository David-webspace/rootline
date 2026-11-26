package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.dept.DepartmentEvent;
import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/** 事件庫：只能附加，不能修改或刪除。 */
public interface DepartmentEventStore {

    /** 依版本順序載入單一部門的全部事件；不存在時回傳空 list。 */
    List<DepartmentEvent> load(TenantId tenantId, DepartmentId departmentId);

    /** 版本大於 {@code afterVersion} 的事件（從快照接續重播用）。 */
    List<DepartmentEvent> loadAfter(TenantId tenantId, DepartmentId departmentId, long afterVersion);

    /** 版本大於 {@code afterVersion} 且發生時間不晚於 {@code until} 的事件（時光機用）。 */
    List<DepartmentEvent> loadUpTo(TenantId tenantId, DepartmentId departmentId, long afterVersion, Instant until);

    /** 完整歷史，含版本號（時光機的事件時間軸）。 */
    List<StoredEvent> history(TenantId tenantId, DepartmentId departmentId);

    /**
     * 附加新事件。若事件庫裡該部門的版本已不是 {@code expectedVersion}（別人先寫了），
     * 丟出 CONFLICT，讓整個 transaction rollback。
     */
    void append(TenantId tenantId, DepartmentId departmentId, long expectedVersion, List<DepartmentEvent> events);

    /** 依全域寫入順序逐筆走訪某租戶的所有事件（重建投影用，不一次載入記憶體）。 */
    void forEachInTenant(TenantId tenantId, Consumer<DepartmentEvent> consumer);

    record StoredEvent(long version, DepartmentEvent event) {
    }
}
