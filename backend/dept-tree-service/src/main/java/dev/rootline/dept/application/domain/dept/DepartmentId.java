package dev.rootline.dept.application.domain.dept;

import dev.rootline.dept.application.domain.shared.DomainException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 部門識別碼。可由 client 指定（批次建樹時前端先產生 id 才能組出巢狀結構），
 * 沒指定就由伺服器產生 UUID。建立後不可變。
 */
public record DepartmentId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    public DepartmentId {
        if (value == null || !FORMAT.matcher(value.trim()).matches()) {
            throw DomainException.invalid("DEPARTMENT_ID_INVALID", "Department id must be 1-64 chars of A-Z, a-z, 0-9, '_' or '-'");
        }
        value = value.trim();
    }

    public static DepartmentId of(String value) {
        return new DepartmentId(value);
    }

    public static DepartmentId generate() {
        return new DepartmentId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
