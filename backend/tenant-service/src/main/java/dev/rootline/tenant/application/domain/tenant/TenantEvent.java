package dev.rootline.tenant.application.domain.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * 租戶生命週期事件，全部發佈到同一個 topic {@code rootline.tenant.lifecycle}，以 tenantId 為 key，
 * 消費端看到的順序與發生順序一致（不會先收到停權才收到開通）。
 */
public sealed interface TenantEvent {

    UUID eventId();

    Instant occurredAt();

    String tenantId();

    String operator();

    default String eventType() {
        return getClass().getSimpleName();
    }

    /**
     * 開通。只帶管理員密碼的 bcrypt hash，絕不帶明文：事件會進 outbox 表與 Kafka，
     * 任何能讀 topic 的人都不該拿到可直接登入的密碼。
     */
    record TenantProvisioned(UUID eventId, Instant occurredAt, String tenantId, String operator, String companyName,
                             String plan, String adminUsername, String adminEmail,
                             String adminPasswordHash) implements TenantEvent {

        @Override
        public String toString() {
            return "TenantProvisioned[tenantId=" + tenantId + ", plan=" + plan + ", admin=" + adminUsername + "]";
        }
    }

    record TenantSuspended(UUID eventId, Instant occurredAt, String tenantId, String operator,
                           String reason) implements TenantEvent {
    }

    record TenantReactivated(UUID eventId, Instant occurredAt, String tenantId,
                             String operator) implements TenantEvent {
    }

    record TenantPlanUpgraded(UUID eventId, Instant occurredAt, String tenantId, String operator, String oldPlan,
                              String newPlan) implements TenantEvent {
    }
}
