package dev.rootline.kyc.infra;

import dev.rootline.kyc.application.domain.kyc.KycApplication.KycStatusChanged;
import dev.rootline.kyc.application.port.KycPorts.DocumentFingerprinter;
import dev.rootline.kyc.application.port.KycPorts.DomainEventOutbox;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

final class KycInfrastructure {

    private KycInfrastructure() {
    }

    /**
     * HMAC-SHA256 指紋。用 HMAC 而不是單純 SHA-256：身分證號空間很小，
     * 沒有金鑰的單向雜湊可以被暴力列舉還原；金鑰只在服務端（環境變數），資料庫外洩也還原不了。
     */
    @Component
    static class HmacDocumentFingerprinter implements DocumentFingerprinter {

        private static final int MIN_KEY_LENGTH = 32;

        private final SecretKeySpec key;

        HmacDocumentFingerprinter(@Value("${rootline.kyc.fingerprint-key:}") String secret) {
            if (secret == null || secret.length() < MIN_KEY_LENGTH) {
                throw new IllegalStateException("rootline.kyc.fingerprint-key (ROOTLINE_KYC_FINGERPRINT_KEY) must be set "
                        + "to at least " + MIN_KEY_LENGTH + " characters");
            }
            this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        }

        @Override
        public String fingerprint(String canonicalDocument) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(key);
                return HexFormat.of().formatHex(mac.doFinal(canonicalDocument.getBytes(StandardCharsets.UTF_8)));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Cannot compute document fingerprint", e);
            }
        }
    }

    @Component
    static class JdbcDomainEventOutbox implements DomainEventOutbox {

        static final String TOPIC = "rootline.kyc.status";

        private final JdbcTemplate jdbc;
        private final JsonMapper json;

        JdbcDomainEventOutbox(JdbcTemplate jdbc, JsonMapper json) {
            this.jdbc = jdbc;
            this.json = json;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public void append(Collection<KycStatusChanged> events) {
            for (KycStatusChanged e : events) {
                jdbc.update("""
                        INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, topic, payload, created_at)
                        VALUES (?, 'KycApplication', ?, 'KycStatusChanged', ?, ?::jsonb, ?)
                        """, e.eventId(), e.tenantId() + ":" + e.userId(), TOPIC, json.writeValueAsString(e),
                        Timestamp.from(e.occurredAt()));
            }
        }
    }
}
