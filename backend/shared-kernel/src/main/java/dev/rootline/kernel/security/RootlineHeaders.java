package dev.rootline.kernel.security;

/**
 * Gateway 驗完 JWT 後注入給下游服務的 header。
 * 下游服務只信任這些 header，因為 Gateway 會先清掉 client 自己帶來的同名 header。
 */
public final class RootlineHeaders {

    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String USER_PERMISSIONS = "X-User-Permissions";
    public static final String TRACE_ID = "X-Trace-Id";

    private RootlineHeaders() {
    }
}
