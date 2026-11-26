package dev.rootline.auth.application.domain.group;

import dev.rootline.auth.application.domain.group.GroupEvent.GroupCreated;
import dev.rootline.auth.application.domain.group.GroupEvent.GroupMemberAdded;
import dev.rootline.auth.application.domain.group.GroupEvent.GroupMemberRemoved;
import dev.rootline.auth.application.domain.group.GroupEvent.GroupRenamed;
import dev.rootline.auth.application.domain.group.GroupEvent.GroupRoleAssigned;
import dev.rootline.auth.application.domain.group.GroupEvent.GroupRoleRevoked;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.AggregateRoot;
import dev.rootline.auth.application.domain.shared.BusinessCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 群組聚合根：成員透過群組繼承角色。成員與角色都只存參照（username / role code），
 * 重複加入或移除不存在的項目都是冪等的 no-op，不產生事件。
 */
public class Group extends AggregateRoot {

    private final UUID id;
    private final TenantId tenantId;
    private final GroupCode code;
    private String name;
    private final Set<Username> members;
    private final Set<RoleCode> roles;

    private Group(UUID id, TenantId tenantId, GroupCode code, String name, Set<Username> members,
                  Set<RoleCode> roles) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.code = Objects.requireNonNull(code);
        this.name = BusinessCode.requireName(name, "GROUP_NAME_INVALID", "Group name");
        this.members = new LinkedHashSet<>(members);
        this.roles = new LinkedHashSet<>(roles);
    }

    public static Group create(TenantId tenantId, GroupCode code, String name) {
        Group group = new Group(UUID.randomUUID(), tenantId, code, name, Set.of(), Set.of());
        group.raise(new GroupCreated(UUID.randomUUID(), Instant.now(), tenantId.value(), group.id.toString(),
                code.value(), group.name));
        return group;
    }

    public static Group rehydrate(UUID id, TenantId tenantId, GroupCode code, String name, Set<Username> members,
                                  Set<RoleCode> roles) {
        return new Group(id, tenantId, code, name, members, roles);
    }

    public void rename(String newName) {
        String normalized = BusinessCode.requireName(newName, "GROUP_NAME_INVALID", "Group name");
        if (normalized.equals(name)) {
            return;
        }
        name = normalized;
        raise(new GroupRenamed(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(), code.value(), name));
    }

    public void addMember(Username username) {
        if (members.add(Objects.requireNonNull(username))) {
            raise(new GroupMemberAdded(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    code.value(), username.value()));
        }
    }

    public void removeMember(Username username) {
        if (members.remove(username)) {
            raise(new GroupMemberRemoved(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    code.value(), username.value()));
        }
    }

    public void assignRole(RoleCode roleCode) {
        if (roles.add(Objects.requireNonNull(roleCode))) {
            raise(new GroupRoleAssigned(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    code.value(), roleCode.value()));
        }
    }

    public void revokeRole(RoleCode roleCode) {
        if (roles.remove(roleCode)) {
            raise(new GroupRoleRevoked(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    code.value(), roleCode.value()));
        }
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public GroupCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Set<Username> members() {
        return Collections.unmodifiableSet(members);
    }

    public Set<RoleCode> roles() {
        return Collections.unmodifiableSet(roles);
    }
}
