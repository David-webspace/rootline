package dev.rootline.auth.application.domain.user;

import dev.rootline.auth.application.domain.shared.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw DomainException.invalid("EMAIL_INVALID", "Email is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !FORMAT.matcher(value).matches()) {
            throw DomainException.invalid("EMAIL_INVALID", "Email format is invalid");
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
