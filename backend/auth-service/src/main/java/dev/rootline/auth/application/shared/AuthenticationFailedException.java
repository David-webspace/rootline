package dev.rootline.auth.application.shared;

/**
 * 登入失敗。訊息刻意固定，不區分「帳號不存在」「密碼錯」「租戶不存在」「已停用」，
 * 避免被拿來列舉帳號。
 */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Invalid tenant, username or password");
    }
}
