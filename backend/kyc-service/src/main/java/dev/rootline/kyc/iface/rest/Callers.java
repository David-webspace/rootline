package dev.rootline.kyc.iface.rest;

import dev.rootline.kyc.application.domain.shared.TenantId;
import dev.rootline.kernel.security.CallerContext;

/** 從 Gateway 注入的呼叫者資訊取出 tenant。受保護的 endpoint 已由攔截器保證身分存在。 */
final class Callers {

    private Callers() {
    }

    static TenantId tenant(CallerContext caller) {
        return TenantId.of(caller.tenantId());
    }

    static boolean isSelf(CallerContext caller, String username) {
        return caller.userId() != null && caller.userId().equalsIgnoreCase(username);
    }
}
