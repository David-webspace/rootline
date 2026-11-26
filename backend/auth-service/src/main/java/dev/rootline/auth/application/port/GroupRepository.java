package dev.rootline.auth.application.port;

import dev.rootline.auth.application.domain.group.Group;
import dev.rootline.auth.application.domain.group.GroupCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import java.util.List;
import java.util.Optional;

public interface GroupRepository {

    Optional<Group> findByCode(TenantId tenantId, GroupCode code);

    List<Group> findByMember(TenantId tenantId, Username member);

    List<Group> findAll(TenantId tenantId);

    boolean exists(TenantId tenantId, GroupCode code);

    void save(Group group);
}
