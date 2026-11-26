package dev.rootline.gateway.security;

import dev.rootline.kernel.security.RootlineHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 包住原始 request：client 自己帶來的身分 header（不分大小寫）全部隱藏，
 * 只露出 Gateway 驗完 JWT 後注入的版本。Gateway MVC 轉發時讀的就是這裡的 header。
 */
public class TrustedHeadersRequest extends HttpServletRequestWrapper {

    /** client 不得自帶的 header。X-Trace-Id 不在此列，由 TraceIdFilter 另外驗格式。 */
    static final Set<String> RESERVED = Set.of(
            RootlineHeaders.TENANT_ID.toLowerCase(Locale.ROOT),
            RootlineHeaders.USER_ID.toLowerCase(Locale.ROOT),
            RootlineHeaders.USER_PERMISSIONS.toLowerCase(Locale.ROOT));

    private final Map<String, String> injected;

    public TrustedHeadersRequest(HttpServletRequest request, Map<String, String> injected) {
        super(request);
        Map<String, String> normalized = new LinkedHashMap<>();
        injected.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
        this.injected = Collections.unmodifiableMap(normalized);
    }

    /** 不注入任何身分，只把 client 偽造的移除（公開路徑使用）。 */
    public static TrustedHeadersRequest anonymous(HttpServletRequest request) {
        return new TrustedHeadersRequest(request, Map.of());
    }

    @Override
    public String getHeader(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (injected.containsKey(key)) {
            return injected.get(key);
        }
        return RESERVED.contains(key) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (injected.containsKey(key)) {
            return Collections.enumeration(List.of(injected.get(key)));
        }
        return RESERVED.contains(key) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = new ArrayList<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original.hasMoreElements()) {
            String name = original.nextElement();
            String key = name.toLowerCase(Locale.ROOT);
            if (!RESERVED.contains(key) && !injected.containsKey(key)) {
                names.add(name);
            }
        }
        names.addAll(injected.keySet());
        return Collections.enumeration(names);
    }
}
