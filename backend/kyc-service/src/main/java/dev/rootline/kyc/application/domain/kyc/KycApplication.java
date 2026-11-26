package dev.rootline.kyc.application.domain.kyc;

import dev.rootline.kyc.application.domain.kyc.IdentityDocument.DocumentType;
import dev.rootline.kyc.application.domain.shared.DomainException;
import dev.rootline.kyc.application.domain.shared.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 一個使用者在一個租戶裡的實名審核案件。
 * <pre>
 * UNVERIFIED ──送審──▶ PENDING_REVIEW ──核准──▶ VERIFIED（之後不可再改）
 *                          │
 *                          └──駁回──▶ REJECTED ──重新送審──▶ PENDING_REVIEW
 * </pre>
 * 職務分離：審核人不能審自己的案件。有風險標記（證件重複使用、未成年）的案件不能核准，只能駁回。
 */
public final class KycApplication {

    public static final String FLAG_DUPLICATE_DOCUMENT = "DUPLICATE_DOCUMENT";
    public static final String FLAG_UNDERAGE = "UNDERAGE";

    private final TenantId tenantId;
    private final String userId;
    private KycStatus status;
    private PersonalDetails details;
    private DocumentType documentType;
    private String documentCountry;
    private String maskedNumber;
    private String fingerprint;
    private final Set<String> flags = new LinkedHashSet<>();
    private Instant submittedAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String rejectReason;
    private long version;
    private final List<KycStatusChanged> pending = new ArrayList<>();

    private KycApplication(TenantId tenantId, String userId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.status = KycStatus.UNVERIFIED;
    }

    public static KycApplication start(TenantId tenantId, String userId) {
        return new KycApplication(tenantId, userId);
    }

    public static KycApplication rehydrate(TenantId tenantId, String userId, KycStatus status, PersonalDetails details,
                                           DocumentType documentType, String documentCountry, String maskedNumber,
                                           String fingerprint, Set<String> flags, Instant submittedAt,
                                           String reviewedBy, Instant reviewedAt, String rejectReason, long version) {
        KycApplication app = new KycApplication(tenantId, userId);
        app.status = status;
        app.details = details;
        app.documentType = documentType;
        app.documentCountry = documentCountry;
        app.maskedNumber = maskedNumber;
        app.fingerprint = fingerprint;
        app.flags.addAll(flags);
        app.submittedAt = submittedAt;
        app.reviewedBy = reviewedBy;
        app.reviewedAt = reviewedAt;
        app.rejectReason = rejectReason;
        app.version = version;
        return app;
    }

    /**
     * @param fingerprint 證件的 HMAC 指紋（由 application 層用伺服器金鑰計算）
     * @param flags       送審時自動偵測到的風險標記
     */
    public void submit(PersonalDetails details, IdentityDocument document, String fingerprint, Set<String> flags) {
        if (status == KycStatus.VERIFIED) {
            throw DomainException.violation("KYC_ALREADY_VERIFIED", "Identity is already verified and cannot be changed");
        }
        if (status == KycStatus.PENDING_REVIEW) {
            throw DomainException.violation("KYC_UNDER_REVIEW", "A submission is already under review");
        }
        KycStatus old = status;
        this.details = details;
        this.documentType = document.type();
        this.documentCountry = document.countryCode();
        this.maskedNumber = document.masked();
        this.fingerprint = fingerprint;
        this.flags.clear();
        this.flags.addAll(flags);
        this.submittedAt = Instant.now();
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.rejectReason = null;
        transition(old, KycStatus.PENDING_REVIEW, null, null);
    }

    public void approve(String reviewer) {
        requireReviewable(reviewer);
        if (!flags.isEmpty()) {
            throw DomainException.violation("KYC_FLAGGED",
                    "Flagged submissions " + flags + " cannot be approved; reject with a reason instead");
        }
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        transition(KycStatus.PENDING_REVIEW, KycStatus.VERIFIED, reviewer, null);
    }

    public void reject(String reviewer, String reason) {
        requireReviewable(reviewer);
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw DomainException.invalid("REJECT_REASON_REQUIRED", "A reason (max 500 chars) is required so the user can fix it");
        }
        reviewedBy = reviewer;
        reviewedAt = Instant.now();
        rejectReason = reason.trim();
        transition(KycStatus.PENDING_REVIEW, KycStatus.REJECTED, reviewer, rejectReason);
    }

    private void requireReviewable(String reviewer) {
        if (status != KycStatus.PENDING_REVIEW) {
            throw DomainException.violation("KYC_NOT_PENDING", "Only submissions under review can be decided");
        }
        if (reviewer == null || reviewer.isBlank()) {
            throw DomainException.invalid("OPERATOR_REQUIRED", "Reviewer is required");
        }
        if (reviewer.equalsIgnoreCase(userId)) {
            throw DomainException.violation("SELF_REVIEW_FORBIDDEN", "Reviewers cannot decide on their own submission");
        }
    }

    private void transition(KycStatus from, KycStatus to, String reviewer, String reason) {
        status = to;
        version++;
        pending.add(new KycStatusChanged(UUID.randomUUID(), Instant.now(), tenantId.value(), userId, from.name(),
                to.name(), reviewer, reason));
    }

    public List<KycStatusChanged> pullEvents() {
        List<KycStatusChanged> events = List.copyOf(pending);
        pending.clear();
        return events;
    }

    /** 狀態變更事件。只帶狀態，不帶姓名、生日、證號等個資。 */
    public record KycStatusChanged(UUID eventId, Instant occurredAt, String tenantId, String userId, String oldStatus,
                                   String newStatus, String reviewer, String reason) {
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public KycStatus status() {
        return status;
    }

    public PersonalDetails details() {
        return details;
    }

    public DocumentType documentType() {
        return documentType;
    }

    public String documentCountry() {
        return documentCountry;
    }

    public String maskedNumber() {
        return maskedNumber;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public Set<String> flags() {
        return Set.copyOf(flags);
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public String reviewedBy() {
        return reviewedBy;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public String rejectReason() {
        return rejectReason;
    }

    public long version() {
        return version;
    }
}
