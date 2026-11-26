package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.user.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash hash);

    /**
     * 帳號不存在時也做一次等成本的比對，讓回應時間無法用來判斷帳號是否存在。
     */
    void burnEquivalentTime(String rawPassword);
}
