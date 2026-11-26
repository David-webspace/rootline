package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.shared.TenantId;

/**
 * 同一租戶的結構異動（建立、搬移、刪除、合併）互斥，鎖在 transaction 結束時自動釋放。
 * <p>
 * 沒有這把鎖會出現經典的併發成環：A 搬到 B 底下、B 同時搬到 A 底下，
 * 兩個 transaction 各自檢查時都看不到對方的修改，都會通過。
 */
public interface TreeLock {

    void lockTenant(TenantId tenantId);
}
