-- 身分資訊（email、帳號狀態、實名狀態）由 auth / kyc 事件投影；details 與 preferences 由本人維護
CREATE TABLE user_profiles (
    tenant_id      VARCHAR(32)  NOT NULL,
    username       VARCHAR(64)  NOT NULL,
    email          VARCHAR(254) NOT NULL DEFAULT '',
    account_status VARCHAR(16)  NOT NULL,
    kyc_status     VARCHAR(16)  NOT NULL,
    details        JSONB        NOT NULL,
    preferences    JSONB        NOT NULL,
    version        BIGINT       NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (tenant_id, username)
);
