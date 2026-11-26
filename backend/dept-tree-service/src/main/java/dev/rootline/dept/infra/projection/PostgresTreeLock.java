package dev.rootline.dept.infra.projection;

import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.TreeLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用 PostgreSQL transaction-level advisory lock 讓同租戶的結構異動排隊。
 * 鎖跟著資料庫 transaction 走：commit / rollback 自動釋放，服務當掉也不會留下死鎖。
 * 不同租戶互不影響。
 */
@Component
class PostgresTreeLock implements TreeLock {

    private final JdbcTemplate jdbc;

    PostgresTreeLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockTenant(TenantId tenantId) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?)) IS NULL",
                Boolean.class, "dept-tree:" + tenantId.value());
    }
}
