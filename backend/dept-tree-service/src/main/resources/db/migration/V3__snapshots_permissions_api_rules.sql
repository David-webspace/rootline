-- 聚合快照：每 N 個事件存一份，載入與時光機查詢從最近的快照接續重播
CREATE TABLE department_snapshots (
    tenant_id     VARCHAR(32) NOT NULL,
    aggregate_id  VARCHAR(64) NOT NULL,
    version       BIGINT      NOT NULL,
    last_event_at TIMESTAMPTZ NOT NULL,
    state         JSONB       NOT NULL,
    PRIMARY KEY (tenant_id, aggregate_id, version)
);

-- 權限定義（權威來源）。version 每次異動 +1，auth 端用它判斷事件新舊
CREATE TABLE permission_definitions (
    tenant_id       VARCHAR(32)  NOT NULL,
    system_code     VARCHAR(32)  NOT NULL,
    permission_code VARCHAR(64)  NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NOT NULL DEFAULT '',
    version         BIGINT       NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    PRIMARY KEY (tenant_id, system_code, permission_code)
);

-- 動態 API 規則（第二軌權限）：比對 method + path pattern，要求額外的權限
CREATE TABLE api_resource_rules (
    id                  UUID         PRIMARY KEY,
    tenant_id           VARCHAR(32)  NOT NULL,
    http_method         VARCHAR(8)   NOT NULL,
    path_pattern        VARCHAR(200) NOT NULL,
    required_permission VARCHAR(100) NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    description         VARCHAR(300) NOT NULL DEFAULT '',
    version             BIGINT       NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    CONSTRAINT uq_api_rules UNIQUE (tenant_id, http_method, path_pattern, required_permission)
);

CREATE INDEX idx_api_rules_tenant ON api_resource_rules (tenant_id, enabled);
