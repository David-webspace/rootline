package dev.rootline.gateway.security;

import dev.rootline.gateway.config.GatewayProperties;
import dev.rootline.gateway.security.JwtVerifier.VerifiedCaller;
import dev.rootline.gateway.web.GatewayResponses;
import dev.rootline.kernel.security.RootlineHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 所有 {@code /api/**} 請求的身分把關：
 * <ul>
 *   <li>公開路徑：放行，但移除 client 偽造的身分 header</li>
 *   <li>其他路徑：必須帶有效 Bearer JWT，否則 401；通過後以 JWT claims 覆蓋身分 header</li>
 * </ul>
 * 驗證後的呼叫者存在 request attribute，給後面的限流 filter 當 key。
 */
public class AuthenticationFilter extends OncePerRequestFilter {

    public static final String CALLER_ATTRIBUTE = AuthenticationFilter.class.getName() + ".caller";
    private static final String BEARER = "Bearer ";

    private final JwtVerifier verifier;
    private final GatewayProperties properties;

    public AuthenticationFilter(JwtVerifier verifier, GatewayProperties properties) {
        this.verifier = verifier;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !normalizedPath(request).startsWith("/api/");
    }

    /**
     * 用 container 正規化後的路徑判斷，不用 {@code getRequestURI()}：後者是原始字串，
     * {@code /x/../api/users} 會被誤判成非 /api 路徑而跳過驗證，但路由比對時卻是 /api/users。
     */
    static String normalizedPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        return (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPublic(request)) {
            chain.doFilter(TrustedHeadersRequest.anonymous(request), response);
            return;
        }

        Optional<VerifiedCaller> caller = bearerToken(request).flatMap(verifier::verify);
        if (caller.isEmpty()) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            GatewayResponses.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED",
                    "A valid access token is required");
            return;
        }

        VerifiedCaller verified = caller.get();
        request.setAttribute(CALLER_ATTRIBUTE, verified);
        chain.doFilter(new TrustedHeadersRequest(request, Map.of(
                RootlineHeaders.TENANT_ID, verified.tenantId(),
                RootlineHeaders.USER_ID, verified.userId(),
                RootlineHeaders.USER_PERMISSIONS, String.join(",", verified.permissions()))), response);
    }

    private boolean isPublic(HttpServletRequest request) {
        String method = request.getMethod();
        String path = normalizedPath(request);
        return properties.publicPaths().stream().anyMatch(p -> p.matches(method, path));
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
