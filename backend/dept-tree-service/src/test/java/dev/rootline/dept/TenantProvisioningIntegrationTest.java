package dev.rootline.dept;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentReadModel;
import dev.rootline.dept.application.service.TenantProvisioningService;
import dev.rootline.dept.application.service.TenantProvisioningService.TenantEvent;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TenantProvisioningIntegrationTest {

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    DepartmentReadModel readModel;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void provisioningCreatesRootAndBuiltInPermissionsExactlyOnce() {
        String tenant = "acme-" + UUID.randomUUID().toString().substring(0, 6);
        UUID eventId = UUID.randomUUID();
        TenantEvent event = new TenantEvent(eventId, "TenantProvisioned", tenant,
                Map.of("companyName", "Acme Robotics", "adminUsername", "owner"));

        assertThat(provisioning.apply(event)).isTrue();
        var root = readModel.find(TenantId.of(tenant), DepartmentId.of("root")).orElseThrow();
        assertThat(root.name()).isEqualTo("Acme Robotics");
        assertThat(root.parentId()).isNull();

        Integer definitions = jdbc.queryForObject("SELECT count(*) FROM permission_definitions WHERE tenant_id = ?",
                Integer.class, tenant);
        assertThat(definitions).isEqualTo(13);
        Integer outbox = jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE event_type = 'PermissionDefined' AND payload->>'tenantId' = ?
                """, Integer.class, tenant);
        assertThat(outbox).as("每個權限定義都要通知 auth").isEqualTo(13);

        // 同一事件重送：完全不動作
        assertThat(provisioning.apply(event)).isFalse();
        // 不同 eventId 但同租戶（例如上游重試產生新事件）：也不會重複建立
        assertThat(provisioning.apply(new TenantEvent(UUID.randomUUID(), "TenantProvisioned", tenant,
                Map.of("companyName", "Acme Robotics")))).isTrue();
        Integer roots = jdbc.queryForObject("SELECT count(*) FROM department_view WHERE tenant_id = ? AND parent_id IS NULL",
                Integer.class, tenant);
        assertThat(roots).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permission_definitions WHERE tenant_id = ?",
                Integer.class, tenant)).isEqualTo(13);
    }

    @Test
    void otherLifecycleEventsAreIgnored() {
        String tenant = "acme-" + UUID.randomUUID().toString().substring(0, 6);
        assertThat(provisioning.apply(new TenantEvent(UUID.randomUUID(), "TenantSuspended", tenant,
                Map.of("reason", "x")))).isFalse();
        assertThat(readModel.find(TenantId.of(tenant), DepartmentId.of("root"))).isEmpty();
    }
}
