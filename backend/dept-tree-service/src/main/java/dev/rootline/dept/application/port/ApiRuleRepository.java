package dev.rootline.dept.application.port;

import dev.rootline.dept.application.domain.rule.ApiRule;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentReadModel.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiRuleRepository {

    Optional<ApiRule> find(TenantId tenantId, UUID id);

    Page<ApiRule> page(TenantId tenantId, int page, int size);

    List<ApiRule> enabled(TenantId tenantId);

    /** 同一組（method, pattern, permission）已存在時丟出 CONFLICT。 */
    void insert(ApiRule rule, String operator);

    void update(ApiRule rule, long expectedVersion, String operator);
}
