package dev.rootline.kernel.security;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 引入 shared-kernel 的 servlet 服務會自動註冊權限攔截器與 {@link CallerContext} 參數解析。
 * 服務若提供自己的 {@link DynamicRulePort} bean，就會取代預設的「無動態規則」。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RootlineSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DynamicRulePort noDynamicRules() {
        return (tenantId, httpMethod, path) -> List.of();
    }

    @Bean
    PermissionGuardInterceptor permissionGuardInterceptor(DynamicRulePort dynamicRules) {
        return new PermissionGuardInterceptor(dynamicRules);
    }

    @Bean
    WebMvcConfigurer rootlineSecurityWebMvcConfigurer(PermissionGuardInterceptor guard) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(guard).addPathPatterns("/api/**");
            }

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CallerContextArgumentResolver());
            }
        };
    }
}
