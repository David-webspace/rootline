-- 租戶狀態投影：來源是 tenant-service 的開通 / 停權事件
CREATE TABLE tenant_registry (
    tenant_id  VARCHAR(32) PRIMARY KEY,
    status     VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id                    UUID         PRIMARY KEY,
    tenant_id             VARCHAR(32)  NOT NULL,
    username              VARCHAR(64)  NOT NULL,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_username UNIQUE (tenant_id, username)
);

CREATE TABLE user_roles (
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE roles (
    id          UUID         PRIMARY KEY,
    tenant_id   VARCHAR(32)  NOT NULL,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    system_root BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_tenant_code UNIQUE (tenant_id, role_code)
);

CREATE TABLE role_permissions (
    role_id         UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    system_code     VARCHAR(32) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_id, system_code, permission_code)
);

CREATE TABLE user_groups (
    id         UUID         PRIMARY KEY,
    tenant_id  VARCHAR(32)  NOT NULL,
    group_code VARCHAR(64)  NOT NULL,
    group_name VARCHAR(100) NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_groups_tenant_code UNIQUE (tenant_id, group_code)
);

CREATE TABLE group_members (
    group_id UUID        NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    username VARCHAR(64) NOT NULL,
    PRIMARY KEY (group_id, username)
);

-- 依成員反查所屬群組（登入、permissions-context 都會用到）
CREATE INDEX idx_group_members_username ON group_members (username);

CREATE TABLE group_roles (
    group_id  UUID        NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (group_id, role_code)
);

-- 權限字典投影：來源是 dept-tree-service；version 是來源端的邏輯時鐘
CREATE TABLE permission_dict (
    tenant_id       VARCHAR(32)  NOT NULL,
    system_code     VARCHAR(32)  NOT NULL,
    permission_code VARCHAR(64)  NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NOT NULL DEFAULT '',
    version         BIGINT       NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, system_code, permission_code)
);
