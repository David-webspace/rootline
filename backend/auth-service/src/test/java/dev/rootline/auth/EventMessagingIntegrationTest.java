package dev.rootline.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.port.PermissionDictionary;
import dev.rootline.auth.application.service.UserCommandService;
import dev.rootline.auth.application.service.UserQueryService;
import dev.rootline.auth.infra.persistence.TenantRegistryEntity;
import dev.rootline.auth.infra.persistence.TenantRegistryJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 走真的 Kafka：dept-tree 的事件進來（用 KafkaTemplate 模擬它送出的信封），以及 auth 自己的 outbox 送出去。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EventMessagingIntegrationTest {

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    KafkaConnectionDetails kafkaConnection;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PermissionDictionary dictionary;
    @Autowired
    TenantRegistryJpaRepository tenants;
    @Autowired
    UserCommandService users;
    @Autowired
    UserQueryService userQueries;

    String tenant;

    @BeforeEach
    void tenant() {
        tenant = "t" + UUID.randomUUID().toString().substring(0, 8);
        tenants.save(new TenantRegistryEntity(tenant, TenantRegistryEntity.ACTIVE));
    }

    @Test
    void permissionDefinitionFlowsIntoTheDictionary() throws Exception {
        publishPermission(UUID.randomUUID(), "PermissionDefined", "DEPT", "BUDGET_VIEW", "檢視預算", 1);

        await(() -> nameOf("DEPT", "BUDGET_VIEW") != null);
        assertThat(nameOf("DEPT", "BUDGET_VIEW")).isEqualTo("檢視預算");
    }

    @Test
    void redeliveredEventIsAppliedOnlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        publishPermission(eventId, "PermissionDefined", "DEPT", "AUDIT_EXPORT", "匯出稽核", 1);
        await(() -> nameOf("DEPT", "AUDIT_EXPORT") != null);

        // 同一個 eventId 再送一次（內容被竄改也一樣）：必須被冪等機制擋掉
        publishPermission(eventId, "PermissionDefined", "DEPT", "AUDIT_EXPORT", "被竄改的名稱", 1);
        publishPermission(UUID.randomUUID(), "PermissionUpdated", "DEPT", "AUDIT_EXPORT", "匯出稽核紀錄", 2);
        await(() -> "匯出稽核紀錄".equals(nameOf("DEPT", "AUDIT_EXPORT")));

        Integer seen = jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
        assertThat(seen).isEqualTo(1);
    }

    @Test
    void lateArrivingOldVersionDoesNotOverwriteNewer() throws Exception {
        publishPermission(UUID.randomUUID(), "PermissionUpdated", "DEPT", "HEADCOUNT_VIEW", "第三版", 3);
        await(() -> "第三版".equals(nameOf("DEPT", "HEADCOUNT_VIEW")));

        publishPermission(UUID.randomUUID(), "PermissionUpdated", "DEPT", "HEADCOUNT_VIEW", "第二版（遲到）", 2);
        String marker = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        publishPermission(UUID.randomUUID(), "PermissionDefined", "DEPT", "MARKER_" + marker, "marker", 1);
        await(() -> nameOf("DEPT", "MARKER_" + marker) != null); // 同 partition 之後的訊息已處理完

        assertThat(nameOf("DEPT", "HEADCOUNT_VIEW")).isEqualTo("第三版");
    }

    @Test
    void departmentEventsShowUpInThePermissionContext() throws Exception {
        users.create(TenantId.of(tenant), "mia", "mia@" + tenant + ".example", "Mia-pass-123");

        publishDepartment("DepartmentCreated", "d-platform", Map.of("name", "Platform", "code", "PLATFORM"));
        publishDepartment("EmployeeAssigned", "d-platform", Map.of("employeeId", "mia"));
        await(() -> !departmentsOfMia().isEmpty());
        assertThat(departmentsOfMia()).containsExactly("Platform");

        publishDepartment("DepartmentRenamed", "d-platform", Map.of("oldName", "Platform", "newName", "Platform Engineering"));
        await(() -> departmentsOfMia().contains("Platform Engineering"));

        publishDepartment("EmployeeUnassigned", "d-platform", Map.of("employeeId", "mia"));
        await(() -> departmentsOfMia().isEmpty());
    }

    @Test
    void malformedMessageGoesToTheDeadLetterTopicWithoutBlockingOthers() throws Exception {
        String garbage = "{\"not\":\"an envelope\",\"id\":\"" + UUID.randomUUID() + "\"}";
        try (KafkaConsumer<String, String> dlt = consumer("rootline.dept.permission.DLT")) {
            kafka.send("rootline.dept.permission", "garbage", garbage).get();
            publishPermission(UUID.randomUUID(), "PermissionDefined", "DEPT", "AFTER_POISON", "後面的訊息", 1);

            await(() -> nameOf("DEPT", "AFTER_POISON") != null);
            assertThat(drain(dlt, Duration.ofSeconds(15), garbage)).isTrue();
        }
    }

    @Test
    void outboxRelayPublishesOwnEventsWithEnvelope() throws Exception {
        try (KafkaConsumer<String, String> consumer = consumer("rootline.auth.user")) {
            users.create(TenantId.of(tenant), "leo", "leo@" + tenant + ".example", "Leo-pass-123");

            JsonNode envelope = null;
            Instant deadline = Instant.now().plusSeconds(20);
            while (envelope == null && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode candidate = json.readTree(record.value());
                    if (tenant.equals(candidate.path("tenantId").asString())) {
                        envelope = candidate;
                        assertThat(record.key()).isEqualTo(candidate.path("aggregateId").asString());
                    }
                }
            }
            assertThat(envelope).as("UserRegistered for " + tenant + " should reach Kafka").isNotNull();
            assertThat(envelope.path("eventType").asString()).isEqualTo("UserRegistered");
            assertThat(envelope.path("payload").path("username").asString()).isEqualTo("leo");

            String eventId = envelope.path("eventId").asString();
            await(() -> "PUBLISHED".equals(jdbc.queryForObject(
                    "SELECT status FROM outbox_events WHERE id = ?::uuid", String.class, eventId)));
        }
    }

    @Test
    void tenantLifecycleProvisionsAdminAndControlsLogin() throws Exception {
        String newTenant = "acme-" + UUID.randomUUID().toString().substring(0, 6);
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("Owner-pass-2026");
        UUID provisioned = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", newTenant);
        payload.put("companyName", "Acme Robotics");
        payload.put("plan", "PRO");
        payload.put("adminUsername", "owner");
        payload.put("adminEmail", "owner@acme.example");
        payload.put("adminPasswordHash", hash);
        sendTenant(newTenant, provisioned, "TenantProvisioned", payload);

        await(() -> loginSucceeds(newTenant));
        var result = auth.login(newTenant, "owner", "Owner-pass-2026");
        assertThat(result.roles()).containsExactly("TENANT_ADMIN");
        assertThat(result.permissions()).containsExactly("*:ADMIN_ALL");

        // 重送開通事件：不會出錯也不會重建帳號
        sendTenant(newTenant, provisioned, "TenantProvisioned", payload);
        sendTenant(newTenant, UUID.randomUUID(), "TenantSuspended", Map.of("tenantId", newTenant, "reason", "逾期"));
        await(() -> !loginSucceeds(newTenant));

        sendTenant(newTenant, UUID.randomUUID(), "TenantReactivated", Map.of("tenantId", newTenant));
        await(() -> loginSucceeds(newTenant));
        Integer admins = jdbc.queryForObject("SELECT count(*) FROM users WHERE tenant_id = ?", Integer.class, newTenant);
        assertThat(admins).isEqualTo(1);
    }

    @Autowired
    dev.rootline.auth.application.service.AuthCommandService auth;

    private boolean loginSucceeds(String tenantCode) {
        try {
            auth.login(tenantCode, "owner", "Owner-pass-2026");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void sendTenant(String tenantCode, UUID eventId, String type, Map<String, Object> payload) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", type);
        envelope.put("aggregateType", "Tenant");
        envelope.put("aggregateId", tenantCode);
        envelope.put("tenantId", tenantCode);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", payload);
        kafka.send("rootline.tenant.lifecycle", tenantCode, json.writeValueAsString(envelope)).get();
    }

    // ---------- helpers ----------

    private void publishPermission(UUID eventId, String type, String system, String code, String name, long version)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("tenantId", tenant);
        payload.put("systemCode", system);
        payload.put("permissionCode", code);
        payload.put("name", name);
        payload.put("description", "");
        payload.put("version", version);
        payload.put("operator", "admin");
        send("rootline.dept.permission", eventId, type, system + ":" + code, payload);
    }

    private void publishDepartment(String type, String departmentId, Map<String, Object> fields) throws Exception {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>(fields);
        payload.put("eventId", eventId.toString());
        payload.put("tenantId", tenant);
        payload.put("departmentId", departmentId);
        payload.put("operator", "admin");
        send("rootline.dept.department", eventId, type, departmentId, payload);
    }

    private void send(String topic, UUID eventId, String type, String key, Map<String, Object> payload) throws Exception {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", type);
        envelope.put("aggregateType", topic.endsWith("permission") ? "Permission" : "Department");
        envelope.put("aggregateId", key);
        envelope.put("tenantId", tenant);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", payload);
        kafka.send(topic, key, json.writeValueAsString(envelope)).get();
    }

    private String nameOf(String system, String code) {
        return dictionary.find(TenantId.of(tenant), new PermissionKey(system, code))
                .map(d -> d.name()).orElse(null);
    }

    private List<String> departmentsOfMia() {
        return userQueries.permissionContext(TenantId.of(tenant), "mia").departments().stream()
                .map(d -> d.name()).toList();
    }

    private KafkaConsumer<String, String> consumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        // 等到真的分到 partition，之後送的訊息才收得到
        Instant deadline = Instant.now().plusSeconds(20);
        while (consumer.assignment().isEmpty() && Instant.now().isBefore(deadline)) {
            consumer.poll(Duration.ofMillis(200));
        }
        return consumer;
    }

    private static boolean drain(KafkaConsumer<String, String> consumer, Duration timeout, String expectedValue) {
        Instant deadline = Instant.now().plus(timeout);
        List<String> seen = new ArrayList<>();
        while (Instant.now().isBefore(deadline)) {
            consumer.poll(Duration.ofMillis(500)).forEach(r -> seen.add(r.value()));
            if (seen.contains(expectedValue)) {
                return true;
            }
        }
        return false;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Condition not met within 20s");
    }
}
