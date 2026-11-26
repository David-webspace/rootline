package dev.rootline.userprofile.infra;

import dev.rootline.messaging.consumer.ProcessedEventGuard;
import dev.rootline.userprofile.application.domain.profile.UserProfile;
import dev.rootline.userprofile.application.domain.profile.UserProfile.Details;
import dev.rootline.userprofile.application.domain.profile.UserProfile.Preferences;
import dev.rootline.userprofile.application.domain.shared.DomainException;
import dev.rootline.userprofile.application.domain.shared.TenantId;
import dev.rootline.userprofile.application.port.ProfilePorts.ProcessedEvents;
import dev.rootline.userprofile.application.port.ProfilePorts.ProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

final class ProfileInfrastructure {

    private ProfileInfrastructure() {
    }

    @Component
    static class JdbcProfileRepository implements ProfileRepository {

        private final JdbcTemplate jdbc;
        private final JsonMapper json;

        JdbcProfileRepository(JdbcTemplate jdbc, JsonMapper json) {
            this.jdbc = jdbc;
            this.json = json;
        }

        @Override
        public Optional<UserProfile> find(TenantId tenantId, String username) {
            return jdbc.query("SELECT * FROM user_profiles WHERE tenant_id = ? AND username = ?",
                    (rs, i) -> UserProfile.rehydrate(tenantId, rs.getString("username"), rs.getString("email"),
                            rs.getString("account_status"), rs.getString("kyc_status"),
                            json.readValue(rs.getString("details"), Details.class),
                            json.readValue(rs.getString("preferences"), Preferences.class), rs.getLong("version")),
                    tenantId.value(), username).stream().findFirst();
        }

        @Override
        public void save(UserProfile p, boolean isNew) {
            String details = json.writeValueAsString(p.details());
            String preferences = json.writeValueAsString(p.preferences());
            if (isNew) {
                try {
                    jdbc.update("""
                            INSERT INTO user_profiles (tenant_id, username, email, account_status, kyc_status, details,
                                                       preferences, version, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, 1, now())
                            """, p.tenantId().value(), p.username(), p.email(), p.accountStatus(), p.kycStatus(),
                            details, preferences);
                } catch (DuplicateKeyException e) {
                    throw conflict();
                }
                return;
            }
            int updated = jdbc.update("""
                    UPDATE user_profiles SET email = ?, account_status = ?, kyc_status = ?, details = ?::jsonb,
                           preferences = ?::jsonb, version = version + 1, updated_at = now()
                    WHERE tenant_id = ? AND username = ? AND version = ?
                    """, p.email(), p.accountStatus(), p.kycStatus(), details, preferences, p.tenantId().value(),
                    p.username(), p.version());
            if (updated == 0) {
                throw conflict();
            }
        }

        private static DomainException conflict() {
            return DomainException.conflict("CONCURRENT_MODIFICATION", "The profile was modified concurrently; retry");
        }
    }

    @Component
    static class GuardedProcessedEvents implements ProcessedEvents {

        private final ProcessedEventGuard guard;

        GuardedProcessedEvents(ProcessedEventGuard guard) {
            this.guard = guard;
        }

        @Override
        public boolean markProcessed(UUID eventId, String consumer) {
            return guard.firstTime(eventId, consumer);
        }
    }
}
