package dev.rootline.dept.application.domain.dept;

import dev.rootline.dept.application.domain.shared.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** 部門代碼，例如 {@code ENG-PLATFORM}。租戶內唯一（含已刪除的部門，復原時才不會撞號）。 */
public record DepartmentCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,49}$");

    public DepartmentCode {
        if (value == null) {
            throw DomainException.invalid("DEPARTMENT_CODE_INVALID", "Department code is required");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw DomainException.invalid("DEPARTMENT_CODE_INVALID",
                    "Department code must be 1-50 chars of A-Z, 0-9, '_' or '-'");
        }
    }

    public static DepartmentCode of(String value) {
        return new DepartmentCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
