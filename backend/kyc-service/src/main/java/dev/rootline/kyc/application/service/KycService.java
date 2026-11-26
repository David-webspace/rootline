package dev.rootline.kyc.application.service;

import dev.rootline.kyc.application.domain.kyc.IdentityDocument;
import dev.rootline.kyc.application.domain.kyc.IdentityDocument.DocumentType;
import dev.rootline.kyc.application.domain.kyc.KycApplication;
import dev.rootline.kyc.application.domain.kyc.KycStatus;
import dev.rootline.kyc.application.domain.kyc.PersonalDetails;
import dev.rootline.kyc.application.domain.shared.DomainException;
import dev.rootline.kyc.application.domain.shared.TenantId;
import dev.rootline.kyc.application.port.KycPorts.DocumentFingerprinter;
import dev.rootline.kyc.application.port.KycPorts.DomainEventOutbox;
import dev.rootline.kyc.application.port.KycPorts.KycRepository;
import dev.rootline.kyc.application.port.KycPorts.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class KycService {

    static final int MAX_PAGE_SIZE = 100;

    private final KycRepository repository;
    private final DocumentFingerprinter fingerprinter;
    private final DomainEventOutbox outbox;

    public KycService(KycRepository repository, DocumentFingerprinter fingerprinter, DomainEventOutbox outbox) {
        this.repository = repository;
        this.fingerprinter = fingerprinter;
        this.outbox = outbox;
    }

    /** 本人送審。風險檢查在這裡做，結果以標記的形式交給審核人判斷。 */
    public View submit(TenantId tenantId, String userId, Submission s) {
        IdentityDocument document = new IdentityDocument(documentType(s.documentType()), s.countryCode(), s.idNumber());
        PersonalDetails details = new PersonalDetails(s.firstName(), s.lastName(), s.dateOfBirth(),
                new PersonalDetails.Address(s.addressCountry(), s.addressState(), s.addressCity(),
                        s.addressPostalCode(), s.addressDetail()));
        String fingerprint = fingerprinter.fingerprint(document.canonical());

        Set<String> flags = new LinkedHashSet<>();
        if (repository.fingerprintUsedByOther(tenantId, fingerprint, userId)) {
            flags.add(KycApplication.FLAG_DUPLICATE_DOCUMENT);
        }
        if (details.isUnderage(LocalDate.now())) {
            flags.add(KycApplication.FLAG_UNDERAGE);
        }

        var existing = repository.find(tenantId, userId);
        KycApplication application = existing.orElseGet(() -> KycApplication.start(tenantId, userId));
        long expected = existing.isPresent() ? application.version() : -1;
        application.submit(details, document, fingerprint, flags);
        persist(application, expected);
        return View.of(application);
    }

    @Transactional(readOnly = true)
    public View mine(TenantId tenantId, String userId) {
        return repository.find(tenantId, userId).map(View::of).orElse(View.unverified(userId));
    }

    public View approve(TenantId tenantId, String userId, String reviewer) {
        KycApplication application = load(tenantId, userId);
        long expected = application.version();
        application.approve(reviewer);
        persist(application, expected);
        return View.of(application);
    }

    public View reject(TenantId tenantId, String userId, String reviewer, String reason) {
        KycApplication application = load(tenantId, userId);
        long expected = application.version();
        application.reject(reviewer, reason);
        persist(application, expected);
        return View.of(application);
    }

    /** 審核佇列：預設列出待審案件，可只看有風險標記的。 */
    @Transactional(readOnly = true)
    public Page<View> queue(TenantId tenantId, String status, boolean flaggedOnly, int page, int size) {
        KycStatus filter = status == null || status.isBlank() ? KycStatus.PENDING_REVIEW : status(status);
        Page<KycApplication> result = repository.queue(tenantId, filter, flaggedOnly, Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return new Page<>(result.content().stream().map(View::of).toList(), result.totalElements(), result.page(),
                result.size());
    }

    private KycApplication load(TenantId tenantId, String userId) {
        return repository.find(tenantId, userId)
                .orElseThrow(() -> DomainException.notFound("KYC_NOT_FOUND", "No KYC submission for " + userId));
    }

    private void persist(KycApplication application, long expectedVersion) {
        repository.save(application, expectedVersion);
        outbox.append(application.pullEvents());
    }

    private static DocumentType documentType(String value) {
        try {
            return DocumentType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("DOCUMENT_TYPE_INVALID", "Document type must be NATIONAL_ID, PASSPORT or RESIDENT_PERMIT");
        }
    }

    private static KycStatus status(String value) {
        try {
            return KycStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("STATUS_INVALID", "Unknown KYC status " + value);
        }
    }

    public record Submission(String firstName, String lastName, LocalDate dateOfBirth, String documentType,
                             String countryCode, String idNumber, String addressCountry, String addressState,
                             String addressCity, String addressPostalCode, String addressDetail) {

        @Override
        public String toString() {
            return "Submission[documentType=" + documentType + ", countryCode=" + countryCode + "]";
        }
    }

    /** 回傳給前端的檢視。證號一律遮罩，不論是本人還是審核人。 */
    public record View(String userId, String status, String firstName, String lastName, LocalDate dateOfBirth,
                       String documentType, String countryCode, String maskedIdNumber, PersonalDetails.Address address,
                       List<String> flags, Instant submittedAt, String reviewedBy, Instant reviewedAt,
                       String rejectReason) {

        static View of(KycApplication a) {
            PersonalDetails d = a.details();
            return new View(a.userId(), a.status().name(), d == null ? null : d.firstName(),
                    d == null ? null : d.lastName(), d == null ? null : d.dateOfBirth(),
                    a.documentType() == null ? null : a.documentType().name(), a.documentCountry(), a.maskedNumber(),
                    d == null ? null : d.address(), a.flags().stream().sorted().toList(), a.submittedAt(),
                    a.reviewedBy(), a.reviewedAt(), a.rejectReason());
        }

        static View unverified(String userId) {
            return new View(userId, KycStatus.UNVERIFIED.name(), null, null, null, null, null, null, null, List.of(),
                    null, null, null, null);
        }
    }
}
