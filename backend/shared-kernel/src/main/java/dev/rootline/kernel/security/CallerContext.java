package dev.rootline.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 目前這個 request 的呼叫者。值來自 Gateway 注入的 header。
 * Controller 可以直接宣告 {@code CallerContext caller} 參數取得。
 */
public record CallerContext(String tenantId, String userId, Set<String> permissions) {

    public CallerContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static CallerContext from(HttpServletRequest request) {
        String header = request.getHeader(RootlineHeaders.USER_PERMISSIONS);
        Set<String> permissions = header == null || header.isBlank()
                ? Set.of()
                : Arrays.stream(header.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        return new CallerContext(
                blankToNull(request.getHeader(RootlineHeaders.TENANT_ID)),
                blankToNull(request.getHeader(RootlineHeaders.USER_ID)),
                permissions);
    }

    public boolean isAuthenticated() {
        return tenantId != null && userId != null;
    }

    public boolean hasPermission(String required) {
        return PermissionEvaluator.isGranted(permissions, required);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
