CREATE TABLE tenants (
    tenant_id      VARCHAR(32)  PRIMARY KEY,
    company_name   VARCHAR(200) NOT NULL,
    plan           VARCHAR(16)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    suspend_reason VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0
);
