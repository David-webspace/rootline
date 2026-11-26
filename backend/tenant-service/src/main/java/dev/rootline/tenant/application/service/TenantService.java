package dev.rootline.tenant.application.service;

import dev.rootline.tenant.application.domain.shared.DomainException;
import dev.rootline.tenant.application.domain.shared.TenantId;
import dev.rootline.tenant.application.domain.tenant.PlanType;
import dev.rootline.tenant.application.domain.tenant.Tenant;
import dev.rootline.tenant.application.domain.tenant.TenantStatus;
import dev.rootline.tenant.application.port.TenantPorts.DomainEventOutbox;
import dev.rootline.tenant.application.port.TenantPorts.Page;
import dev.rootline.tenant.application.port.TenantPorts.PasswordHasher;
import dev.rootline.tenant.application.port.TenantPorts.TenantRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {

    static final int MAX_PAGE_SIZE = 100;

    private final TenantRepository tenants;
    private final PasswordHasher hasher;
    private final DomainEventOutbox outbox;

    public TenantService(TenantRepository tenants, PasswordHasher hasher, DomainEventOutbox outbox) {
        this.tenants = tenants;
        this.hasher = hasher;
        this.outbox = outbox;
    }

    /** 開通。管理員密碼在這裡就轉成 bcrypt hash，明文不會進資料庫、outbox 或 Kafka。 */
    public View provision(String tenantId, String companyName, String plan, String adminUsername, String adminEmail,
                          String adminPassword, String operator) {
        Tenant tenant = Tenant.provision(TenantId.of(tenantId), companyName, plan(plan), adminUsername, adminEmail,
                hasher.hash(adminPassword), operator);
        tenants.insert(tenant);
        outbox.append(tenant.pullEvents());
        return View.of(tenant);
    }

    public View suspend(String tenantId, String reason, String operator) {
        return change(tenantId, t -> t.suspend(reason, operator));
    }

    public View reactivate(String tenantId, String operator) {
        return change(tenantId, t -> t.reactivate(operator));
    }

    public View upgradePlan(String tenantId, String plan, String operator) {
        return change(tenantId, t -> t.upgradePlan(plan(plan), operator));
    }

    @Transactional(readOnly = true)
    public View get(String tenantId) {
        return View.of(load(tenantId));
    }

    @Transactional(readOnly = true)
    public Page<View> search(String keyword, String status, int page, int size) {
        TenantStatus filter = status == null || status.isBlank() ? null : status(status);
        Page<Tenant> result = tenants.search(keyword, filter, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return new Page<>(result.content().stream().map(View::of).toList(), result.totalElements(), result.page(),
                result.size());
    }

    private View change(String tenantId, Consumer<Tenant> action) {
        Tenant tenant = load(tenantId);
        long expected = tenant.version();
        action.accept(tenant);
        if (tenant.version() != expected) {
            tenants.update(tenant, expected);
            outbox.append(tenant.pullEvents());
        }
        return View.of(tenant);
    }

    private Tenant load(String tenantId) {
        TenantId id = TenantId.of(tenantId);
        return tenants.find(id).orElseThrow(() -> DomainException.notFound("TENANT_NOT_FOUND", "Tenant " + id + " not found"));
    }

    private static PlanType plan(String value) {
        try {
            return PlanType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("PLAN_INVALID", "Plan must be FREE, PRO or ENTERPRISE");
        }
    }

    private static TenantStatus status(String value) {
        try {
            return TenantStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("STATUS_INVALID", "Status must be ACTIVE or SUSPENDED");
        }
    }

    public record View(String tenantId, String companyName, String plan, String status, String suspendReason,
                       Instant createdAt, Instant expiresAt) {

        static View of(Tenant t) {
            return new View(t.id().value(), t.companyName(), t.plan().name(), t.status().name(), t.suspendReason(),
                    t.createdAt(), t.expiresAt());
        }
    }
}
