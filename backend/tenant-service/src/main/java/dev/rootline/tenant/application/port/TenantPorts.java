package dev.rootline.tenant.application.port;

import dev.rootline.tenant.application.domain.shared.TenantId;
import dev.rootline.tenant.application.domain.tenant.Tenant;
import dev.rootline.tenant.application.domain.tenant.TenantEvent;
import dev.rootline.tenant.application.domain.tenant.TenantStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** tenant-service 對外界的所有依賴（服務小，集中在一個檔案）。 */
public final class TenantPorts {

    private TenantPorts() {
    }

    public interface TenantRepository {

        Optional<Tenant> find(TenantId id);

        Page<Tenant> search(String keyword, TenantStatus status, int page, int size);

        /** 已存在時丟出 CONFLICT。 */
        void insert(Tenant tenant);

        /** 樂觀鎖更新。 */
        void update(Tenant tenant, long expectedVersion);
    }

    public interface PasswordHasher {

        /** 檢查密碼政策並回傳 bcrypt hash。 */
        String hash(String rawPassword);
    }

    public interface DomainEventOutbox {

        void append(Collection<? extends TenantEvent> events);
    }

    public record Page<T>(List<T> content, long totalElements, int page, int size) {
    }
}
