package dev.rootline.auth.application.domain.user;

public enum UserStatus {
    /** 可正常登入。 */
    ACTIVE,
    /** 連續登入失敗被鎖定，需管理員解鎖。 */
    LOCKED,
    /** 已停用（邏輯刪除），需管理員復權。 */
    DEACTIVATED
}
