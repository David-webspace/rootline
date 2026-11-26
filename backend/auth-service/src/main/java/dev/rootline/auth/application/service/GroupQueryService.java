package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.group.Group;
import dev.rootline.auth.application.domain.group.GroupCode;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.GroupRepository;
import dev.rootline.auth.application.shared.Views.GroupView;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupQueryService {

    private final GroupRepository groups;

    public GroupQueryService(GroupRepository groups) {
        this.groups = groups;
    }

    public GroupView get(TenantId tenantId, String groupCode) {
        GroupCode code = GroupCode.of(groupCode);
        return groups.findByCode(tenantId, code)
                .map(GroupQueryService::toView)
                .orElseThrow(() -> DomainException.notFound("GROUP_NOT_FOUND", "Group " + code + " not found"));
    }

    public List<GroupView> list(TenantId tenantId) {
        return groups.findAll(tenantId).stream()
                .sorted(Comparator.comparing(g -> g.code().value()))
                .map(GroupQueryService::toView)
                .toList();
    }

    private static GroupView toView(Group group) {
        return new GroupView(group.code().value(), group.name(),
                group.members().stream().map(Username::value).sorted().toList(),
                group.roles().stream().map(RoleCode::value).sorted().toList());
    }
}
