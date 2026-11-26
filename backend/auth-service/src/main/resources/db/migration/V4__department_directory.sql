-- dept-tree-service 的部門投影：只保留 auth 需要的部分（名稱、是否刪除、誰在哪個部門），
-- 給 permissions-context 顯示使用者所屬部門。來源事件：rootline.dept.department
CREATE TABLE dept_directory (
    tenant_id     VARCHAR(32)  NOT NULL,
    department_id VARCHAR(64)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, department_id)
);

CREATE TABLE user_departments (
    tenant_id     VARCHAR(32) NOT NULL,
    username      VARCHAR(64) NOT NULL,
    department_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, username, department_id)
);
