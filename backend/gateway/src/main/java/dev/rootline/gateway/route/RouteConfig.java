package dev.rootline.gateway.route;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import dev.rootline.gateway.config.GatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * 路由表。每個下游服務一個熔斷器；只有 502/503/504（基礎設施層失敗）才計入失敗並轉到 fallback，
 * 下游自己回的 4xx / 500 照原樣傳回，避免把有意義的錯誤訊息蓋掉。
 */
@Configuration(proxyBeanMethods = false)
class RouteConfig {

    private static final String[] INFRA_FAILURES = {"502", "503", "504"};

    @Bean
    RouterFunction<ServerResponse> authRoutes(GatewayProperties properties) {
        return service("auth", properties,
                "/api/auth/**", "/api/users/**", "/api/roles/**", "/api/groups/**", "/api/permissions/**");
    }

    @Bean
    RouterFunction<ServerResponse> deptRoutes(GatewayProperties properties) {
        return service("dept", properties, "/api/departments/**");
    }

    @Bean
    RouterFunction<ServerResponse> tenantRoutes(GatewayProperties properties) {
        return service("tenant", properties, "/api/platform/tenants/**");
    }

    @Bean
    RouterFunction<ServerResponse> kycRoutes(GatewayProperties properties) {
        return service("kyc", properties, "/api/kyc/**");
    }

    @Bean
    RouterFunction<ServerResponse> profileRoutes(GatewayProperties properties) {
        return service("profile", properties, "/api/profiles/**");
    }

    private static RouterFunction<ServerResponse> service(String name, GatewayProperties properties,
                                                          String... paths) {
        return route(name + "-service")
                .route(path(paths), http())
                .before(uri(properties.serviceUri(name)))
                .filter(circuitBreaker(config -> config
                        .setId(name)
                        .setFallbackUri("forward:/fallback/" + name)
                        .setStatusCodes(INFRA_FAILURES)))
                .build();
    }
}
