package dev.rootline.userprofile.application.domain.profile;

import dev.rootline.userprofile.application.domain.shared.DomainException;
import dev.rootline.userprofile.application.domain.shared.TenantId;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 使用者個人檔案。分兩部分：
 * <ul>
 *   <li>身分資訊（email、帳號狀態、實名狀態）：唯讀，來自 auth / kyc 的事件</li>
 *   <li>自訂資料（顯示名稱、職稱、電話、自介、外部連結、通知偏好）：本人維護</li>
 * </ul>
 * 跨 topic 的事件順序不保證（kyc 的事件可能比註冊事件先到），所以每一部分都可以獨立先建立。
 */
public final class UserProfile {

    public static final int MAX_LINKS = 5;
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{6,20}$");
    private static final Set<String> LANGUAGES = Set.of("zh-TW", "en");

    private final TenantId tenantId;
    private final String username;
    private String email;
    private String accountStatus;
    private String kycStatus;
    private Details details;
    private Preferences preferences;
    private long version;

    private UserProfile(TenantId tenantId, String username) {
        this.tenantId = tenantId;
        this.username = username;
        this.email = "";
        this.accountStatus = "UNKNOWN";
        this.kycStatus = "UNVERIFIED";
        this.details = Details.empty();
        this.preferences = Preferences.defaults();
    }

    public static UserProfile blank(TenantId tenantId, String username) {
        if (username == null || username.isBlank()) {
            throw DomainException.invalid("USERNAME_INVALID", "Username is required");
        }
        return new UserProfile(tenantId, username.trim().toLowerCase(Locale.ROOT));
    }

    public static UserProfile rehydrate(TenantId tenantId, String username, String email, String accountStatus,
                                        String kycStatus, Details details, Preferences preferences, long version) {
        UserProfile profile = new UserProfile(tenantId, username);
        profile.email = email;
        profile.accountStatus = accountStatus;
        profile.kycStatus = kycStatus;
        profile.details = details;
        profile.preferences = preferences;
        profile.version = version;
        return profile;
    }

    // ---------- 來自其他服務的事件 ----------

    public void syncIdentity(String email, String accountStatus) {
        if (email != null) {
            this.email = email;
        }
        if (accountStatus != null) {
            this.accountStatus = accountStatus;
        }
    }

    public void syncKycStatus(String kycStatus) {
        this.kycStatus = kycStatus;
    }

    // ---------- 本人維護 ----------

    public void update(Details newDetails, Preferences newPreferences) {
        this.details = newDetails;
        this.preferences = newPreferences;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String accountStatus() {
        return accountStatus;
    }

    public String kycStatus() {
        return kycStatus;
    }

    public Details details() {
        return details;
    }

    public Preferences preferences() {
        return preferences;
    }

    public long version() {
        return version;
    }

    public record Details(String displayName, String jobTitle, String phone, String bio, List<Link> links) {

        public Details {
            displayName = optional(displayName, 80, "Display name");
            jobTitle = optional(jobTitle, 80, "Job title");
            bio = optional(bio, 500, "Bio");
            phone = optional(phone, 20, "Phone");
            if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
                throw DomainException.invalid("PHONE_INVALID", "Phone may contain digits, spaces, (), - and a leading +");
            }
            links = links == null ? List.of() : List.copyOf(links);
            if (links.size() > MAX_LINKS) {
                throw DomainException.invalid("LINKS_TOO_MANY", "At most " + MAX_LINKS + " links");
            }
        }

        public static Details empty() {
            return new Details("", "", "", "", List.of());
        }
    }

    /** 外部連結只接受 https，避免 {@code javascript:} 之類的連結被前端渲染成可點擊的 XSS。 */
    public record Link(String label, String url) {

        public Link {
            label = optional(label, 40, "Link label");
            String value = url == null ? "" : url.trim();
            try {
                URI uri = new URI(value);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || value.length() > 300) {
                    throw DomainException.invalid("LINK_INVALID", "Links must be https URLs (max 300 chars)");
                }
            } catch (URISyntaxException e) {
                throw DomainException.invalid("LINK_INVALID", "Link is not a valid URL");
            }
            url = value;
        }
    }

    public record Preferences(boolean emailNotifications, boolean smsNotifications, String language) {

        public Preferences {
            language = language == null || language.isBlank() ? "zh-TW" : language.trim();
            if (!LANGUAGES.contains(language)) {
                throw DomainException.invalid("LANGUAGE_INVALID", "Language must be one of " + LANGUAGES);
            }
        }

        public static Preferences defaults() {
            return new Preferences(true, false, "zh-TW");
        }
    }

    private static String optional(String value, int max, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > max) {
            throw DomainException.invalid("PROFILE_FIELD_TOO_LONG", label + " must be at most " + max + " chars");
        }
        return trimmed;
    }
}
