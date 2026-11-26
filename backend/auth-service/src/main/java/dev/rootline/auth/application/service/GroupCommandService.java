package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.group.Group;
import dev.rootline.auth.application.domain.group.GroupCode;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.DomainEventOutbox;
import dev.rootline.auth.application.port.GroupRepository;
import dev.rootline.auth.application.port.RoleRepository;
import dev.rootline.auth.application.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupCommandService {

    private final GroupRepository groups;
    private final UserRepository users;
    private final RoleRepository roles;
    private final DomainEventOutbox outbox;

    public GroupCommandService(GroupRepository groups, UserRepository users, RoleRepository roles,
                               DomainEventOutbox outbox) {
        this.groups = groups;
        this.users = users;
        this.roles = roles;
        this.outbox = outbox;
    }

    public String create(TenantId tenantId, String groupCode, String groupName) {
        GroupCode code = GroupCode.of(groupCode);
        if (groups.exists(tenantId, code)) {
            throw DomainException.conflict("GROUP_CODE_TAKEN", "Group " + code + " already exists");
        }
        Group group = Group.create(tenantId, code, groupName);
        persist(group);
        return group.code().value();
    }

    public void rename(TenantId tenantId, String groupCode, String newName) {
        Group group = load(tenantId, groupCode);
        group.rename(newName);
        persist(group);
    }

    public void addMember(TenantId tenantId, String groupCode, String username) {
        Group group = load(tenantId, groupCode);
        Username member = Username.of(username);
        if (!users.exists(tenantId, member)) {
            throw DomainException.notFound("USER_NOT_FOUND", "User " + member + " not found");
        }
        group.addMember(member);
        persist(group);
    }

    public void removeMember(TenantId tenantId, String groupCode, String username) {
        Group group = load(tenantId, groupCode);
        group.removeMember(Username.of(username));
        persist(group);
    }

    public void assignRole(TenantId tenantId, String groupCode, String roleCode) {
        Group group = load(tenantId, groupCode);
        RoleCode code = RoleCode.of(roleCode);
        if (!roles.exists(tenantId, code)) {
            throw DomainException.notFound("ROLE_NOT_FOUND", "Role " + code + " not found");
        }
        group.assignRole(code);
        persist(group);
    }

    public void revokeRole(TenantId tenantId, String groupCode, String roleCode) {
        Group group = load(tenantId, groupCode);
        group.revokeRole(RoleCode.of(roleCode));
        persist(group);
    }

    private Group load(TenantId tenantId, String groupCode) {
        GroupCode code = GroupCode.of(groupCode);
        return groups.findByCode(tenantId, code)
                .orElseThrow(() -> DomainException.notFound("GROUP_NOT_FOUND", "Group " + code + " not found"));
    }

    private void persist(Group group) {
        groups.save(group);
        outbox.append(group.pullEvents());
    }
}
