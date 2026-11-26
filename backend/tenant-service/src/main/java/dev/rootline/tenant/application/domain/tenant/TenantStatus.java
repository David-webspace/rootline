package dev.rootline.tenant.application.domain.tenant;

public enum TenantStatus {
    /** 正常營運，成員可登入。 */
    ACTIVE,
    /** 停權（欠費或違規），成員無法登入；可由平台人員恢復。 */
    SUSPENDED
}
