package dev.rootline.tenant.application.domain.shared;

/**
 * 違反領域規則時丟出。{@link Kind} 讓介面層決定 HTTP 狀態碼，
 * {@code code} 是給前端判斷用的穩定錯誤代碼。
 */
public class DomainException extends RuntimeException {

    public enum Kind {
        INVALID_INPUT,
        NOT_FOUND,
        CONFLICT,
        RULE_VIOLATION
    }

    private final Kind kind;
    private final String code;

    public DomainException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public static DomainException invalid(String code, String message) {
        return new DomainException(Kind.INVALID_INPUT, code, message);
    }

    public static DomainException notFound(String code, String message) {
        return new DomainException(Kind.NOT_FOUND, code, message);
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(Kind.CONFLICT, code, message);
    }

    public static DomainException violation(String code, String message) {
        return new DomainException(Kind.RULE_VIOLATION, code, message);
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
