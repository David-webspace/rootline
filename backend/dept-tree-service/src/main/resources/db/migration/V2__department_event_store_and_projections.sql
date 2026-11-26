-- ============ 寫入端：事件庫（唯一真相，只能附加）============
CREATE TABLE department_events (
    seq          BIGSERIAL    PRIMARY KEY,           -- 全域寫入順序，重建投影時依此重播
    tenant_id    VARCHAR(32)  NOT NULL,
    aggregate_id VARCHAR(64)  NOT NULL,
    version      BIGINT       NOT NULL,              -- 該部門的第幾個事件，從 1 開始
    event_id     UUID         NOT NULL UNIQUE,
    event_type   VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,
    operator     VARCHAR(64)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    -- 樂觀鎖：兩個 transaction 同時寫同一個版本號，後到的撞 unique
    CONSTRAINT uq_department_events_version UNIQUE (tenant_id, aggregate_id, version)
);

CREATE INDEX idx_department_events_tenant_seq ON department_events (tenant_id, seq);

-- ============ 讀取端：投影（可隨時從事件庫重建）============
CREATE TABLE department_view (
    tenant_id        VARCHAR(32)  NOT NULL,
    id               VARCHAR(64)  NOT NULL,
    parent_id        VARCHAR(64),
    code             VARCHAR(50)  NOT NULL,
    name             VARCHAR(200) NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    merged_into      VARCHAR(64),
    direct_headcount INT          NOT NULL DEFAULT 0,
    version          BIGINT       NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       VARCHAR(64)  NOT NULL,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_department_view_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_department_view_parent ON department_view (tenant_id, parent_id);

-- 閉包表：每一對（祖先, 子孫）一列，含 depth=0 的自己對自己
CREATE TABLE department_tree (
    tenant_id     VARCHAR(32) NOT NULL,
    ancestor_id   VARCHAR(64) NOT NULL,
    descendant_id VARCHAR(64) NOT NULL,
    depth         INT         NOT NULL,
    PRIMARY KEY (tenant_id, ancestor_id, descendant_id)
);

CREATE INDEX idx_department_tree_descendant ON department_tree (tenant_id, descendant_id);

CREATE TABLE department_employees (
    tenant_id     VARCHAR(32) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    employee_id   VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, department_id, employee_id)
);

CREATE INDEX idx_department_employees_employee ON department_employees (tenant_id, employee_id);
