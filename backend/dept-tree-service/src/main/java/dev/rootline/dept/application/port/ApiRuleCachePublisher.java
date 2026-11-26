package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.rule.ApiRule;
import dev.rootline.dept.application.domain.shared.TenantId;
import java.util.List;

/** 把租戶目前啟用的規則整份寫到共享快取（Redis），各服務的權限攔截器從那裡讀。 */
public interface ApiRuleCachePublisher {

    void publish(TenantId tenantId, List<ApiRule> enabledRules);
}
