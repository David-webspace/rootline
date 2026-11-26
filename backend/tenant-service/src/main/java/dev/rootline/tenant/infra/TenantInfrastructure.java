package dev.rootline.tenant.infra;

import dev.rootline.tenant.application.domain.shared.DomainException;
import dev.rootline.tenant.application.domain.tenant.TenantEvent;
import dev.rootline.tenant.application.port.TenantPorts.DomainEventOutbox;
import dev.rootline.tenant.application.port.TenantPorts.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** tenant-service 的小型 adapter。 */
final class TenantInfrastructure {

    private TenantInfrastructure() {
    }

    /** 與 auth-service 相同的密碼政策與演算法，auth 收到 hash 後可直接拿來驗證登入。 */
    @Component
    static class BCryptPasswordHasher implements PasswordHasher {

        private final BCryptPasswordEncoder encoder;

        BCryptPasswordHasher(@Value("${rootline.security.bcrypt-strength:12}") int strength) {
            this.encoder = new BCryptPasswordEncoder(strength);
        }

        @Override
        public String hash(String rawPassword) {
            if (rawPassword == null || rawPassword.length() < 8) {
                throw DomainException.invalid("PASSWORD_INVALID", "Admin password must be at least 8 chars");
            }
            if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw DomainException.invalid("PASSWORD_INVALID", "Admin password must be at most 72 bytes");
            }
            return encoder.encode(rawPassword);
        }
    }

    @Component
    static class JdbcDomainEventOutbox implements DomainEventOutbox {

        static final String TOPIC = "rootline.tenant.lifecycle";

        private final JdbcTemplate jdbc;
        private final JsonMapper json;

        JdbcDomainEventOutbox(JdbcTemplate jdbc, JsonMapper json) {
            this.jdbc = jdbc;
            this.json = json;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public void append(Collection<? extends TenantEvent> events) {
            for (TenantEvent e : events) {
                jdbc.update("""
                        INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at)
                        VALUES (?, 'Tenant', ?, ?, ?, ?::jsonb, ?)
                        """, e.eventId(), e.tenantId(), e.eventType(), TOPIC, json.writeValueAsString(e),
                        Timestamp.from(e.occurredAt()));
            }
        }
    }
}
