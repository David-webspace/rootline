package dev.rootline.auth.application.domain.user;

import dev.rootline.auth.application.domain.shared.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** 登入帳號，租戶內唯一。一律轉小寫，避免 {@code Mia} 與 {@code mia} 變成兩個人。 */
public record Username(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");

    public Username {
        if (value == null) {
            throw DomainException.invalid("USERNAME_INVALID", "Username is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw DomainException.invalid("USERNAME_INVALID",
                    "Username must be 3-64 chars of a-z, 0-9, '.', '_' or '-'");
        }
    }

    public static Username of(String value) {
        return new Username(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
