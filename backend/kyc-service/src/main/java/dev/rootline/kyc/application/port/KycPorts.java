package dev.rootline.kyc.application.port;

import dev.rootline.kyc.application.domain.kyc.KycApplication;
import dev.rootline.kyc.application.domain.kyc.KycApplication.KycStatusChanged;
import dev.rootline.kyc.application.domain.kyc.KycStatus;
import dev.rootline.kyc.application.domain.shared.TenantId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class KycPorts {

    private KycPorts() {
    }

    public interface KycRepository {

        Optional<KycApplication> find(TenantId tenantId, String userId);

        /** 同租戶中，是否有「其他人」用過同一張證件。 */
        boolean fingerprintUsedByOther(TenantId tenantId, String fingerprint, String userId);

        Page<KycApplication> queue(TenantId tenantId, KycStatus status, boolean flaggedOnly, int page, int size);

        /** 新增或樂觀鎖更新（{@code expectedVersion} 為 -1 代表新增）。 */
        void save(KycApplication application, long expectedVersion);
    }

    /** 以伺服器端金鑰計算證件指紋（HMAC）。沒有金鑰的人拿到指紋也反推不出證號。 */
    public interface DocumentFingerprinter {

        String fingerprint(String canonicalDocument);
    }

    public interface DomainEventOutbox {

        void append(Collection<KycStatusChanged> events);
    }

    public record Page<T>(List<T> content, long totalElements, int page, int size) {
    }
}
