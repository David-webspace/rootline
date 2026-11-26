package dev.rootline.dept.application.domain.dept;

import dev.rootline.dept.application.domain.shared.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 員工識別碼，等同 auth-service 的 username（同樣規則、同樣轉小寫）。
 * dept-tree 不同步查 auth 確認此人存在：兩個服務不共用資料庫，靠事件達成最終一致。
 */
public record EmployeeId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");

    public EmployeeId {
        if (value == null) {
            throw DomainException.invalid("EMPLOYEE_ID_INVALID", "Employee id is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw DomainException.invalid("EMPLOYEE_ID_INVALID", "Employee id must be a valid username");
        }
    }

    public static EmployeeId of(String value) {
        return new EmployeeId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
