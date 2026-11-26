package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.shared.TenantId;

/** auth 本地的租戶狀態投影（來源是 tenant-service 的開通 / 停權事件）。 */
public interface TenantDirectory {

    boolean isActive(TenantId tenantId);

    /** 由 tenant-service 的生命週期事件驅動：開通 / 恢復為 true，停權為 false。 */
    void setActive(TenantId tenantId, boolean active);
}
