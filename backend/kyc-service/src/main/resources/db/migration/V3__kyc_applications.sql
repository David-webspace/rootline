-- 實名審核案件。不存完整證號：只存遮罩號碼（給人看）與 HMAC 指紋（比對重複使用）
CREATE TABLE kyc_applications (
    tenant_id        VARCHAR(32)  NOT NULL,
    user_id          VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    first_name       VARCHAR(50)  NOT NULL,
    last_name        VARCHAR(50)  NOT NULL,
    date_of_birth    DATE         NOT NULL,
    address          JSONB        NOT NULL,
    document_type    VARCHAR(20)  NOT NULL,
    document_country CHAR(2)      NOT NULL,
    masked_number    VARCHAR(24)  NOT NULL,
    fingerprint      CHAR(64)     NOT NULL,
    flags            VARCHAR(200) NOT NULL DEFAULT '',
    submitted_at     TIMESTAMPTZ,
    reviewed_by      VARCHAR(64),
    reviewed_at      TIMESTAMPTZ,
    reject_reason    VARCHAR(500),
    version          BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, user_id)
);

CREATE INDEX idx_kyc_fingerprint ON kyc_applications (tenant_id, fingerprint);
CREATE INDEX idx_kyc_queue ON kyc_applications (tenant_id, status, submitted_at);
