package dev.rootline.kyc.application.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rootline.kyc.application.domain.kyc.IdentityDocument;
import dev.rootline.kyc.application.domain.kyc.IdentityDocument.DocumentType;
import dev.rootline.kyc.application.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

class IdentityDocumentTest {

    @Test
    void acceptsValidTaiwanIdsAndNormalizesInput() {
        // 常見的測試用合法號碼（非真實個人）
        IdentityDocument doc = new IdentityDocument(DocumentType.NATIONAL_ID, "tw", " a123456789 ");
        assertThat(doc.number()).isEqualTo("A123456789");
        assertThat(doc.masked()).isEqualTo("A******789");
        assertThat(doc.canonical()).isEqualTo("NATIONAL_ID|TW|A123456789");
        assertThat(doc.toString()).doesNotContain("123456");
    }

    @Test
    void rejectsTaiwanIdsWithBadChecksumOrShape() {
        for (String bad : new String[]{"A123456788", "A323456789", "1123456789", "A12345678"}) {
            assertThatThrownBy(() -> new IdentityDocument(DocumentType.NATIONAL_ID, "TW", bad))
                    .as(bad)
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo("DOCUMENT_NUMBER_INVALID");
        }
    }

    @Test
    void otherDocumentTypesUseShapeChecks() {
        assertThat(new IdentityDocument(DocumentType.PASSPORT, "JP", "tk1234567").masked()).isEqualTo("T*****567");
        assertThatThrownBy(() -> new IdentityDocument(DocumentType.PASSPORT, "JP", "12"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new IdentityDocument(DocumentType.NATIONAL_ID, "TWN", "A123456789"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("COUNTRY_CODE_INVALID");
    }
}
