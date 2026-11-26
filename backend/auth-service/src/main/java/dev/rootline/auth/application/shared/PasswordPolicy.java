package dev.rootline.auth.application.shared;

import dev.rootline.auth.application.domain.shared.DomainException;
import java.nio.charset.StandardCharsets;

/** 明文密碼規則。上限 72 bytes 是 BCrypt 的實際截斷長度，超過的部分不會被比對。 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw DomainException.invalid("PASSWORD_INVALID", "Password is required");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw DomainException.invalid("PASSWORD_INVALID", "Password must be at least " + MIN_LENGTH + " chars");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw DomainException.invalid("PASSWORD_INVALID", "Password must be at most " + MAX_BYTES + " bytes");
        }
    }
}
