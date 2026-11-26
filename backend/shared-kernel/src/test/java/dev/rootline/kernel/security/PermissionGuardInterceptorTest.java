package dev.rootline.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class PermissionGuardInterceptorTest {

    static class SampleController {
        @RequiresPermission("IAM:USER_READ")
        public void annotated() {
        }

        @RequiresPermission(value = "IAM:USER_READ", selfPathVariable = "username")
        public void selfAllowed() {
        }

        public void unannotated() {
        }

        @Authenticated
        public void signedInOnly() {
        }
    }

    @Test
    void authenticatedOnlyRequiresIdentityButNoPermission() throws Exception {
        MockHttpServletResponse anonymous = new MockHttpServletResponse();
        assertThat(noRules.preHandle(new MockHttpServletRequest("POST", "/api/kyc/submit"), anonymous,
                handler("signedInOnly"))).isFalse();
        assertThat(anonymous.getStatus()).isEqualTo(401);

        assertThat(noRules.preHandle(caller("northwind", "mia", ""), new MockHttpServletResponse(),
                handler("signedInOnly"))).isTrue();
    }

    private final PermissionGuardInterceptor noRules =
            new PermissionGuardInterceptor((t, m, p) -> List.of());

    @Test
    void annotatedEndpointWithoutIdentityReturns401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean passed = noRules.preHandle(new MockHttpServletRequest("GET", "/api/users"), response, handler("annotated"));
        assertThat(passed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void annotatedEndpointWithoutPermissionReturns403() throws Exception {
        MockHttpServletRequest request = caller("northwind", "mia", "IAM:ROLE_READ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(noRules.preHandle(request, response, handler("annotated"))).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void annotatedEndpointWithPermissionPasses() throws Exception {
        MockHttpServletRequest request = caller("northwind", "mia", "IAM:USER_READ,IAM:ROLE_READ");
        assertThat(noRules.preHandle(request, new MockHttpServletResponse(), handler("annotated"))).isTrue();
    }

    @Test
    void selfPathVariableLetsCallerReadOwnResource() throws Exception {
        MockHttpServletRequest request = caller("northwind", "mia", "");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("username", "mia"));
        assertThat(noRules.preHandle(request, new MockHttpServletResponse(), handler("selfAllowed"))).isTrue();
    }

    @Test
    void selfPathVariableDoesNotCoverOtherUsers() throws Exception {
        MockHttpServletRequest request = caller("northwind", "mia", "");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("username", "leo"));
        assertThat(noRules.preHandle(request, new MockHttpServletResponse(), handler("selfAllowed"))).isFalse();
    }

    @Test
    void unannotatedEndpointWithoutDynamicRulePasses() throws Exception {
        assertThat(noRules.preHandle(new MockHttpServletRequest("GET", "/api/x"),
                new MockHttpServletResponse(), handler("unannotated"))).isTrue();
    }

    @Test
    void unannotatedEndpointUsesDynamicRule() throws Exception {
        PermissionGuardInterceptor withRule = new PermissionGuardInterceptor(
                (tenant, method, path) -> "northwind".equals(tenant) && "DELETE".equals(method)
                        ? List.of("DEPT:TREE_WRITE") : List.of());

        MockHttpServletRequest request = caller("northwind", "mia", "IAM:USER_READ");
        request.setMethod("DELETE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(withRule.preHandle(request, response, handler("unannotated"))).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void dynamicRuleTightensAnAnnotatedEndpoint() throws Exception {
        PermissionGuardInterceptor tightened = new PermissionGuardInterceptor(
                (tenant, method, path) -> List.of("IAM:EXPORT"));

        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertThat(tightened.preHandle(caller("northwind", "mia", "IAM:USER_READ"), denied, handler("annotated")))
                .as("靜態權限通過，但租戶政策追加的權限沒有")
                .isFalse();
        assertThat(denied.getContentAsString()).contains("Tenant policy requires permission IAM:EXPORT");

        assertThat(tightened.preHandle(caller("northwind", "mia", "IAM:USER_READ,IAM:EXPORT"),
                new MockHttpServletResponse(), handler("annotated"))).isTrue();
    }

    @Test
    void dynamicRuleCannotLoosenAStaticRequirement() throws Exception {
        PermissionGuardInterceptor withUnrelatedRule = new PermissionGuardInterceptor(
                (tenant, method, path) -> List.of("IAM:SOMETHING_ELSE"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(withUnrelatedRule.preHandle(caller("northwind", "mia", "IAM:SOMETHING_ELSE"), response,
                handler("annotated"))).isFalse();
        assertThat(response.getContentAsString()).contains("Requires permission IAM:USER_READ");
    }

    @Test
    void selfExceptionDoesNotBypassDynamicRules() throws Exception {
        PermissionGuardInterceptor tightened = new PermissionGuardInterceptor(
                (tenant, method, path) -> List.of("IAM:PROFILE_VIEW"));
        MockHttpServletRequest request = caller("northwind", "mia", "");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("username", "mia"));
        assertThat(tightened.preHandle(request, new MockHttpServletResponse(), handler("selfAllowed"))).isFalse();
    }

    private static MockHttpServletRequest caller(String tenant, String user, String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/" + user);
        request.addHeader(RootlineHeaders.TENANT_ID, tenant);
        request.addHeader(RootlineHeaders.USER_ID, user);
        request.addHeader(RootlineHeaders.USER_PERMISSIONS, permissions);
        return request;
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getMethod(methodName);
        return new HandlerMethod(new SampleController(), method);
    }
}
