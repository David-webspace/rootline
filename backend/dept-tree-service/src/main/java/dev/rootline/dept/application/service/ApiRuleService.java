package dev.rootline.dept.application.service;

import dev.rootline.dept.application.domain.rule.ApiRule;
import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.ApiRuleCachePublisher;
import dev.rootline.dept.application.port.ApiRuleRepository;
import dev.rootline.dept.application.port.DepartmentReadModel.Page;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 動態 API 規則的管理。資料庫是權威來源，Redis 只是讓各服務快速讀取的副本：
 * 每次異動 commit 之後才把整份啟用中的規則寫到 Redis，rollback 的修改不會外流。
 * Redis 內容遺失或與資料庫不一致時，呼叫 {@link #republish} 即可重建。
 */
@Service
@Transactional
public class ApiRuleService {

    static final int MAX_PAGE_SIZE = 100;

    private final ApiRuleRepository repository;
    private final ApiRuleCachePublisher publisher;

    public ApiRuleService(ApiRuleRepository repository, ApiRuleCachePublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public View create(TenantId tenantId, String method, String pathPattern, String requiredPermission,
                       String description, String operator) {
        ApiRule rule = ApiRule.create(tenantId, method, pathPattern, requiredPermission, description);
        repository.insert(rule, operator);
        publishAfterCommit(tenantId);
        return View.of(rule);
    }

    public View update(TenantId tenantId, UUID id, String method, String pathPattern, String requiredPermission,
                       String description, String operator) {
        ApiRule rule = load(tenantId, id);
        long expected = rule.version();
        rule.change(method, pathPattern, requiredPermission, description);
        repository.update(rule, expected, operator);
        publishAfterCommit(tenantId);
        return View.of(rule);
    }

    public View setEnabled(TenantId tenantId, UUID id, boolean enabled, String operator) {
        ApiRule rule = load(tenantId, id);
        long expected = rule.version();
        rule.setEnabled(enabled);
        if (rule.version() != expected) {
            repository.update(rule, expected, operator);
            publishAfterCommit(tenantId);
        }
        return View.of(rule);
    }

    @Transactional(readOnly = true)
    public Page<View> page(TenantId tenantId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<ApiRule> result = repository.page(tenantId, Math.max(page, 0), safeSize);
        return new Page<>(result.content().stream().map(View::of).toList(), result.totalElements(), result.page(),
                result.size());
    }

    /** @return 寫到快取的規則數 */
    @Transactional(readOnly = true)
    public int republish(TenantId tenantId) {
        var rules = repository.enabled(tenantId);
        publisher.publish(tenantId, rules);
        return rules.size();
    }

    private ApiRule load(TenantId tenantId, UUID id) {
        return repository.find(tenantId, id)
                .orElseThrow(() -> DomainException.notFound("API_RULE_NOT_FOUND", "API rule " + id + " not found"));
    }

    private void publishAfterCommit(TenantId tenantId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publish(tenantId, repository.enabled(tenantId));
            }
        });
    }

    public record View(UUID id, String method, String pathPattern, String requiredPermission, boolean enabled,
                       String description, long version) {

        static View of(ApiRule r) {
            return new View(r.id(), r.method(), r.pathPattern(), r.requiredPermission(), r.enabled(),
                    r.description(), r.version());
        }
    }
}
