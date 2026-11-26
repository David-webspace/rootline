package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.PasswordHash;
import dev.rootline.auth.application.port.ProcessedEvents;
import dev.rootline.auth.application.port.RoleRepository;
import dev.rootline.auth.application.port.TenantDirectory;
import dev.rootline.auth.application.service.DeptEventProjectionService.IncomingEvent;
import dev.rootline.auth.application.service.DeptEventProjectionService.Outcome;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回應 tenant-service 的生命週期事件：
 * <ul>
 *   <li>開通：啟用租戶、建立 TENANT_ADMIN（*:ADMIN_ALL）與管理員帳號</li>
 *   <li>停權 / 恢復：切換租戶狀態，停權後成員無法再登入</li>
 * </ul>
 * 每一步都先檢查是否已存在，加上 eventId 冪等，重送或部分失敗後重試都安全。
 */
@Service
public class TenantLifecycleService {

    public static final String CONSUMER = "auth-tenant-projector";
    static final String ADMIN_ROLE = "TENANT_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(TenantLifecycleService.class);

    private final ProcessedEvents processed;
    private final TenantDirectory tenants;
    private final RoleRepository roles;
    private final RoleCommandService roleCommands;
    private final UserCommandService userCommands;

    public TenantLifecycleService(ProcessedEvents processed, TenantDirectory tenants, RoleRepository roles,
                                  RoleCommandService roleCommands, UserCommandService userCommands) {
        this.processed = processed;
        this.tenants = tenants;
        this.roles = roles;
        this.roleCommands = roleCommands;
        this.userCommands = userCommands;
    }

    @Transactional
    public Outcome apply(IncomingEvent event) {
        if (!processed.markProcessed(event.eventId(), CONSUMER)) {
            return Outcome.DUPLICATE;
        }
        TenantId tenant = TenantId.of(event.tenantId());
        switch (event.eventType()) {
            case "TenantProvisioned" -> provision(tenant, event.payload());
            case "TenantSuspended" -> tenants.setActive(tenant, false);
            case "TenantReactivated" -> tenants.setActive(tenant, true);
            default -> {
                return Outcome.IGNORED;
            }
        }
        return Outcome.APPLIED;
    }

    private void provision(TenantId tenant, Map<String, Object> payload) {
        tenants.setActive(tenant, true);
        if (!roles.exists(tenant, RoleCode.of(ADMIN_ROLE))) {
            roleCommands.createSystemRoot(tenant, ADMIN_ROLE, "租戶管理員", Set.of(PermissionKey.SUPER_ADMIN));
        }
        String username = text(payload, "adminUsername");
        boolean created = userCommands.createWithPasswordHash(tenant, username, text(payload, "adminEmail"),
                new PasswordHash(text(payload, "adminPasswordHash")));
        userCommands.assignRole(tenant, username, ADMIN_ROLE);
        log.info("Provisioned tenant {} (admin {} {})", tenant, username, created ? "created" : "already existed");
    }

    private static String text(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("TenantProvisioned payload is missing '" + field + "'");
        }
        return s;
    }
}
