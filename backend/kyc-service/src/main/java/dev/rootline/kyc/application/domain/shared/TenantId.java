package dev.rootline.kyc.application.domain.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/** 租戶代碼，例如 {@code northwind}。一律轉小寫。 */
public record TenantId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");

    public TenantId {
        if (value == null) {
            throw DomainException.invalid("TENANT_ID_INVALID", "Tenant id is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw DomainException.invalid("TENANT_ID_INVALID",
                    "Tenant id must be 2-32 chars of a-z, 0-9 or '-'");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
