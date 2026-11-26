package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.User;
import dev.rootline.auth.application.domain.user.Username;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUsername(TenantId tenantId, Username username);

    boolean exists(TenantId tenantId, Username username);

    List<User> findAll(TenantId tenantId);

    void save(User user);
}
