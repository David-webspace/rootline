package dev.rootline.tenant.application.domain.tenant;

import dev.rootline.tenant.application.domain.shared.DomainException;
import dev.rootline.tenant.application.domain.shared.TenantId;
import dev.rootline.tenant.application.domain.tenant.TenantEvent.TenantPlanUpgraded;
import dev.rootline.tenant.application.domain.tenant.TenantEvent.TenantProvisioned;
import dev.rootline.tenant.application.domain.tenant.TenantEvent.TenantReactivated;
import dev.rootline.tenant.application.domain.tenant.TenantEvent.TenantSuspended;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 租戶聚合根。開通後由事件驅動其他服務各自初始化（auth 建管理員、dept-tree 建根部門與權限定義），
 * tenant-service 不直接呼叫它們，也不知道它們的資料結構。
 */
public final class Tenant {

    /** 平台營運人員所在的租戶，保留不可開通。 */
    public static final String PLATFORM = "platform";
    private static final Set<String> RESERVED = Set.of(PLATFORM, "admin", "api", "system", "root");
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final TenantId id;
    private String companyName;
    private PlanType plan;
    private TenantStatus status;
    private String suspendReason;
    private final Instant createdAt;
    private final Instant expiresAt;
    private long version;
    private final List<TenantEvent> pending = new ArrayList<>();

    private Tenant(TenantId id, String companyName, PlanType plan, TenantStatus status, String suspendReason,
                   Instant createdAt, Instant expiresAt, long version) {
        this.id = id;
        this.companyName = companyName;
        this.plan = plan;
        this.status = status;
        this.suspendReason = suspendReason;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.version = version;
    }

    public static Tenant provision(TenantId id, String companyName, PlanType plan, String adminUsername,
                                   String adminEmail, String adminPasswordHash, String operator) {
        if (RESERVED.contains(id.value())) {
            throw DomainException.invalid("TENANT_ID_RESERVED", "Tenant id " + id + " is reserved");
        }
        String name = companyName == null ? "" : companyName.trim();
        if (name.isEmpty() || name.length() > 200) {
            throw DomainException.invalid("COMPANY_NAME_INVALID", "Company name is required (max 200 chars)");
        }
        String username = adminUsername == null ? "" : adminUsername.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw DomainException.invalid("ADMIN_USERNAME_INVALID", "Admin username must be 3-64 chars of a-z, 0-9, '.', '_' or '-'");
        }
        String email = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw DomainException.invalid("ADMIN_EMAIL_INVALID", "Admin email format is invalid");
        }
        if (adminPasswordHash == null || adminPasswordHash.isBlank()) {
            throw DomainException.invalid("ADMIN_PASSWORD_INVALID", "Admin password is required");
        }
        Instant now = Instant.now();
        Tenant tenant = new Tenant(id, name, plan, TenantStatus.ACTIVE, null, now, now.plus(365, ChronoUnit.DAYS), 0);
        tenant.pending.add(new TenantProvisioned(UUID.randomUUID(), now, id.value(), requireOperator(operator), name,
                plan.name(), username, email, adminPasswordHash));
        return tenant;
    }

    public static Tenant rehydrate(TenantId id, String companyName, PlanType plan, TenantStatus status,
                                   String suspendReason, Instant createdAt, Instant expiresAt, long version) {
        return new Tenant(id, companyName, plan, status, suspendReason, createdAt, expiresAt, version);
    }

    public void suspend(String reason, String operator) {
        if (status == TenantStatus.SUSPENDED) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw DomainException.invalid("SUSPEND_REASON_REQUIRED", "A reason is required to suspend a tenant");
        }
        status = TenantStatus.SUSPENDED;
        suspendReason = reason.trim();
        version++;
        pending.add(new TenantSuspended(UUID.randomUUID(), Instant.now(), id.value(), requireOperator(operator),
                suspendReason));
    }

    public void reactivate(String operator) {
        if (status == TenantStatus.ACTIVE) {
            return;
        }
        status = TenantStatus.ACTIVE;
        suspendReason = null;
        version++;
        pending.add(new TenantReactivated(UUID.randomUUID(), Instant.now(), id.value(), requireOperator(operator)));
    }

    public void upgradePlan(PlanType newPlan, String operator) {
        if (status != TenantStatus.ACTIVE) {
            throw DomainException.violation("TENANT_NOT_ACTIVE", "Only active tenants can change plan");
        }
        if (!newPlan.isUpgradeFrom(plan)) {
            throw DomainException.violation("PLAN_NOT_UPGRADE", plan + " → " + newPlan + " is not an upgrade");
        }
        PlanType old = plan;
        plan = newPlan;
        version++;
        pending.add(new TenantPlanUpgraded(UUID.randomUUID(), Instant.now(), id.value(), requireOperator(operator),
                old.name(), newPlan.name()));
    }

    public List<TenantEvent> pullEvents() {
        List<TenantEvent> events = List.copyOf(pending);
        pending.clear();
        return events;
    }

    private static String requireOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            throw DomainException.invalid("OPERATOR_REQUIRED", "Operator is required for the audit trail");
        }
        return operator;
    }

    public TenantId id() {
        return id;
    }

    public String companyName() {
        return companyName;
    }

    public PlanType plan() {
        return plan;
    }

    public TenantStatus status() {
        return status;
    }

    public String suspendReason() {
        return suspendReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public long version() {
        return version;
    }
}
