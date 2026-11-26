package dev.rootline.kyc.application.domain.kyc;

import dev.rootline.kyc.application.domain.shared.DomainException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 身分證件。完整號碼只在送審的那一刻存在於記憶體：
 * 系統只保存遮罩後的號碼（給人看）與 HMAC 指紋（比對是否有人重複使用，由 application 層計算）。
 */
public record IdentityDocument(DocumentType type, String countryCode, String number) {

    public enum DocumentType {
        NATIONAL_ID, PASSPORT, RESIDENT_PERMIT
    }

    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");
    private static final Pattern TW_ID = Pattern.compile("^[A-Z][1289][0-9]{8}$");
    private static final Pattern PASSPORT = Pattern.compile("^[A-Z0-9]{6,9}$");
    private static final Pattern GENERIC = Pattern.compile("^[A-Z0-9-]{4,20}$");

    /** 台灣身分證字首字母對應的兩位數。 */
    private static final Map<Character, Integer> TW_LETTERS = Map.ofEntries(
            Map.entry('A', 10), Map.entry('B', 11), Map.entry('C', 12), Map.entry('D', 13), Map.entry('E', 14),
            Map.entry('F', 15), Map.entry('G', 16), Map.entry('H', 17), Map.entry('I', 34), Map.entry('J', 18),
            Map.entry('K', 19), Map.entry('L', 20), Map.entry('M', 21), Map.entry('N', 22), Map.entry('O', 35),
            Map.entry('P', 23), Map.entry('Q', 24), Map.entry('R', 25), Map.entry('S', 26), Map.entry('T', 27),
            Map.entry('U', 28), Map.entry('V', 29), Map.entry('W', 32), Map.entry('X', 30), Map.entry('Y', 31),
            Map.entry('Z', 33));

    public IdentityDocument {
        if (type == null) {
            throw DomainException.invalid("DOCUMENT_TYPE_INVALID", "Document type is required");
        }
        countryCode = countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
        if (!COUNTRY.matcher(countryCode).matches()) {
            throw DomainException.invalid("COUNTRY_CODE_INVALID", "Country must be an ISO 3166-1 alpha-2 code");
        }
        number = number == null ? "" : number.replace(" ", "").trim().toUpperCase(Locale.ROOT);
        boolean valid = switch (type) {
            case NATIONAL_ID -> "TW".equals(countryCode) ? isValidTaiwanId(number) : GENERIC.matcher(number).matches();
            case PASSPORT -> PASSPORT.matcher(number).matches();
            case RESIDENT_PERMIT -> GENERIC.matcher(number).matches();
        };
        if (!valid) {
            throw DomainException.invalid("DOCUMENT_NUMBER_INVALID", "Document number is not valid for " + type
                    + " (" + countryCode + ")");
        }
    }

    /**
     * 台灣身分證檢查碼：字母轉兩位數後，十位數 ×1、個位數 ×9，接著 8 碼依序 ×8…×1，最後一碼 ×1，總和要被 10 整除。
     */
    static boolean isValidTaiwanId(String id) {
        if (!TW_ID.matcher(id).matches()) {
            return false;
        }
        int letter = TW_LETTERS.get(id.charAt(0));
        int sum = (letter / 10) + (letter % 10) * 9;
        for (int i = 1; i <= 8; i++) {
            sum += (id.charAt(i) - '0') * (9 - i);
        }
        sum += id.charAt(9) - '0';
        return sum % 10 == 0;
    }

    /** 只露第一碼與最後三碼，例如 {@code A******789}。 */
    public String masked() {
        if (number.length() <= 4) {
            return "*".repeat(number.length());
        }
        return number.charAt(0) + "*".repeat(number.length() - 4) + number.substring(number.length() - 3);
    }

    /** 計算指紋用的正規化字串（同一張證件不論輸入格式，結果都一樣）。 */
    public String canonical() {
        return type + "|" + countryCode + "|" + number;
    }

    @Override
    public String toString() {
        return "IdentityDocument[" + type + ", " + countryCode + ", " + masked() + "]";
    }
}
