package dev.rootline.kyc.infra;

import dev.rootline.kyc.application.domain.kyc.IdentityDocument.DocumentType;
import dev.rootline.kyc.application.domain.kyc.KycApplication;
import dev.rootline.kyc.application.domain.kyc.KycStatus;
import dev.rootline.kyc.application.domain.kyc.PersonalDetails;
import dev.rootline.kyc.application.domain.shared.DomainException;
import dev.rootline.kyc.application.domain.shared.TenantId;
import dev.rootline.kyc.application.port.KycPorts.KycRepository;
import dev.rootline.kyc.application.port.KycPorts.Page;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class JdbcKycRepository implements KycRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    JdbcKycRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<KycApplication> find(TenantId tenantId, String userId) {
        return jdbc.query("SELECT * FROM kyc_applications WHERE tenant_id = ? AND user_id = ?",
                (rs, i) -> map(rs, tenantId), tenantId.value(), userId).stream().findFirst();
    }

    @Override
    public boolean fingerprintUsedByOther(TenantId tenantId, String fingerprint, String userId) {
        Boolean used = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM kyc_applications WHERE tenant_id = ? AND fingerprint = ? AND user_id <> ?)
                """, Boolean.class, tenantId.value(), fingerprint, userId);
        return Boolean.TRUE.equals(used);
    }

    @Override
    public Page<KycApplication> queue(TenantId tenantId, KycStatus status, boolean flaggedOnly, int page, int size) {
        String where = "WHERE tenant_id = ? AND status = ? AND (? = FALSE OR flags <> '')";
        Long total = jdbc.queryForObject("SELECT count(*) FROM kyc_applications " + where, Long.class,
                tenantId.value(), status.name(), flaggedOnly);
        List<KycApplication> content = jdbc.query("SELECT * FROM kyc_applications " + where
                        + " ORDER BY submitted_at NULLS LAST LIMIT ? OFFSET ?",
                (rs, i) -> map(rs, tenantId), tenantId.value(), status.name(), flaggedOnly, size, (long) page * size);
        return new Page<>(content, total == null ? 0 : total, page, size);
    }

    @Override
    public void save(KycApplication a, long expectedVersion) {
        PersonalDetails d = a.details();
        Object[] values = {a.status().name(), d.firstName(), d.lastName(), Date.valueOf(d.dateOfBirth()),
                json.writeValueAsString(d.address()), a.documentType().name(), a.documentCountry(), a.maskedNumber(),
                a.fingerprint(), String.join(",", a.flags().stream().sorted().toList()), ts(a.submittedAt()),
                a.reviewedBy(), ts(a.reviewedAt()), a.rejectReason(), a.version()};
        if (expectedVersion < 0) {
            try {
                jdbc.update("""
                        INSERT INTO kyc_applications (status, first_name, last_name, date_of_birth, address,
                            document_type, document_country, masked_number, fingerprint, flags, submitted_at,
                            reviewed_by, reviewed_at, reject_reason, version, tenant_id, user_id)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, append(values, a.tenantId().value(), a.userId()));
            } catch (DuplicateKeyException e) {
                throw conflict();
            }
            return;
        }
        int updated = jdbc.update("""
                UPDATE kyc_applications SET status = ?, first_name = ?, last_name = ?, date_of_birth = ?,
                    address = ?::jsonb, document_type = ?, document_country = ?, masked_number = ?, fingerprint = ?,
                    flags = ?, submitted_at = ?, reviewed_by = ?, reviewed_at = ?, reject_reason = ?, version = ?
                WHERE tenant_id = ? AND user_id = ? AND version = ?
                """, append(values, a.tenantId().value(), a.userId(), expectedVersion));
        if (updated == 0) {
            throw conflict();
        }
    }

    private KycApplication map(ResultSet rs, TenantId tenantId) throws SQLException {
        String flags = rs.getString("flags");
        Set<String> flagSet = flags == null || flags.isBlank() ? Set.of()
                : Arrays.stream(flags.split(",")).collect(Collectors.toSet());
        PersonalDetails details = new PersonalDetails(rs.getString("first_name"), rs.getString("last_name"),
                rs.getDate("date_of_birth").toLocalDate(),
                json.readValue(rs.getString("address"), PersonalDetails.Address.class));
        return KycApplication.rehydrate(tenantId, rs.getString("user_id"), KycStatus.valueOf(rs.getString("status")),
                details, DocumentType.valueOf(rs.getString("document_type")), rs.getString("document_country"),
                rs.getString("masked_number"), rs.getString("fingerprint"), flagSet, instant(rs, "submitted_at"),
                rs.getString("reviewed_by"), instant(rs, "reviewed_at"), rs.getString("reject_reason"),
                rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Object[] append(Object[] values, Object... more) {
        Object[] result = Arrays.copyOf(values, values.length + more.length);
        System.arraycopy(more, 0, result, values.length, more.length);
        return result;
    }

    private static DomainException conflict() {
        return DomainException.conflict("CONCURRENT_MODIFICATION", "The KYC submission was modified concurrently; retry");
    }
}
