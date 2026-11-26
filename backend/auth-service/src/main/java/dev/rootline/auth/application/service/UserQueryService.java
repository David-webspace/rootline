package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.group.Group;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.User;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.DepartmentDirectory;
import dev.rootline.auth.application.port.UserRepository;
import dev.rootline.auth.application.shared.Views.DepartmentView;
import dev.rootline.auth.application.service.EffectiveAccessResolver.EffectiveAccess;
import dev.rootline.auth.application.shared.Views.GroupRoleView;
import dev.rootline.auth.application.shared.Views.PermissionContextView;
import dev.rootline.auth.application.shared.Views.UserView;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository users;
    private final EffectiveAccessResolver access;
    private final PermissionNames permissionNames;
    private final DepartmentDirectory departments;

    public UserQueryService(UserRepository users, EffectiveAccessResolver access, PermissionNames permissionNames,
                            DepartmentDirectory departments) {
        this.users = users;
        this.access = access;
        this.permissionNames = permissionNames;
        this.departments = departments;
    }

    public UserView get(TenantId tenantId, String username) {
        return toView(load(tenantId, username));
    }

    public List<UserView> list(TenantId tenantId) {
        return users.findAll(tenantId).stream()
                .sorted(Comparator.comparing(u -> u.username().value()))
                .map(UserQueryService::toView)
                .toList();
    }

    /** 使用者的完整權限脈絡：個人角色、各群組繼承的角色，以及展開後的權限。 */
    public PermissionContextView permissionContext(TenantId tenantId, String username) {
        User user = load(tenantId, username);
        EffectiveAccess effective = access.resolve(user);

        List<GroupRoleView> groupRoles = effective.groups().stream()
                .sorted(Comparator.comparing(g -> g.code().value()))
                .map(UserQueryService::toGroupRoleView)
                .toList();

        return new PermissionContextView(
                user.username().value(),
                user.email().value(),
                user.status().name(),
                codes(effective.personalRoles()),
                groupRoles,
                permissionNames.describe(tenantId, effective.permissions()),
                departments.departmentsOf(tenantId, user.username()).stream()
                        .map(d -> new DepartmentView(d.id(), d.name())).toList());
    }

    private User load(TenantId tenantId, String username) {
        Username name = Username.of(username);
        return users.findByUsername(tenantId, name)
                .orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "User " + name + " not found"));
    }

    private static UserView toView(User user) {
        return new UserView(user.id().toString(), user.username().value(), user.email().value(),
                user.status().name(), codes(user.roles()));
    }

    private static GroupRoleView toGroupRoleView(Group group) {
        return new GroupRoleView(group.code().value(), group.name(), codes(group.roles()));
    }

    private static List<String> codes(java.util.Collection<RoleCode> roleCodes) {
        return roleCodes.stream().map(RoleCode::value).sorted().toList();
    }
}
