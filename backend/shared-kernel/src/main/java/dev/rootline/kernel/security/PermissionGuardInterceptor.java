package dev.rootline.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 雙軌權限檢查，兩軌都要通過：
 * <ol>
 *   <li>靜態：controller 上的 {@link RequiresPermission}（可用 selfPathVariable 讓本人免權限）</li>
 *   <li>動態：{@link DynamicRulePort} 依租戶設定追加的權限（沒有本人例外）</li>
 * </ol>
 * 兩軌都沒有要求就放行。未帶身分回 401，權限不足回 403，body 與各服務的 ApiResponse 格式一致。
 */
public class PermissionGuardInterceptor implements HandlerInterceptor {

    private final DynamicRulePort dynamicRules;

    public PermissionGuardInterceptor(DynamicRulePort dynamicRules) {
        this.dynamicRules = dynamicRules;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresPermission annotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }
        boolean authenticatedOnly = handlerMethod.hasMethodAnnotation(Authenticated.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Authenticated.class);
        CallerContext caller = CallerContext.from(request);
        List<String> dynamic = caller.tenantId() == null ? List.of()
                : dynamicRules.requiredPermissions(caller.tenantId(), request.getMethod(), request.getRequestURI());

        if (annotation == null && !authenticatedOnly && dynamic.isEmpty()) {
            return true;
        }
        if (!caller.isAuthenticated()) {
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
        }
        if (annotation != null && !isSelf(request, annotation, caller) && !caller.hasPermission(annotation.value())) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "Requires permission " + annotation.value());
        }
        for (String required : dynamic) {
            if (!caller.hasPermission(required)) {
                return reject(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                        "Tenant policy requires permission " + required);
            }
        }
        return true;
    }

    private static boolean isSelf(HttpServletRequest request, RequiresPermission annotation, CallerContext caller) {
        if (annotation.selfPathVariable().isEmpty()) {
            return false;
        }
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return false;
        }
        Object value = variables.get(annotation.selfPathVariable());
        return value != null && value.toString().equalsIgnoreCase(caller.userId());
    }

    private static boolean reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + escape(message) + "\",\"data\":null}");
        return false;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
