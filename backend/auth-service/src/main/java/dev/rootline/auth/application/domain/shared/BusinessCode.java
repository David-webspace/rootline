package dev.rootline.auth.application.domain.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/** 角色、群組等業務代碼的共用格式：大寫英數與底線，例如 {@code HR_VIEWER}。 */
public final class BusinessCode {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private BusinessCode() {
    }

    public static String normalize(String raw, String errorCode, String label) {
        if (raw == null) {
            throw DomainException.invalid(errorCode, label + " is required");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw DomainException.invalid(errorCode,
                    label + " must start with a letter and use A-Z, 0-9 or '_' (2-64 chars)");
        }
        return value;
    }

    public static String requireName(String raw, String errorCode, String label) {
        if (raw == null || raw.isBlank()) {
            throw DomainException.invalid(errorCode, label + " is required");
        }
        String value = raw.trim();
        if (value.length() > 100) {
            throw DomainException.invalid(errorCode, label + " must be at most 100 chars");
        }
        return value;
    }
}
