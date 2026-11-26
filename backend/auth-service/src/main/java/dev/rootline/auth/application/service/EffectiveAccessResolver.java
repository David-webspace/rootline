package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.group.Group;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.role.Role;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.user.User;
import dev.rootline.auth.application.port.GroupRepository;
import dev.rootline.auth.application.port.RoleRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 算出使用者實際擁有的角色與權限：個人角色 ∪ 所屬群組的角色，再展開成權限。
 * 登入簽 JWT 與 permissions-context 查詢共用同一套計算，避免兩邊結果不一致。
 */
@Component
public class EffectiveAccessResolver {

    private final RoleRepository roles;
    private final GroupRepository groups;

    public EffectiveAccessResolver(RoleRepository roles, GroupRepository groups) {
        this.roles = roles;
        this.groups = groups;
    }

    public EffectiveAccess resolve(User user) {
        List<Group> memberOf = groups.findByMember(user.tenantId(), user.username());

        Set<RoleCode> allRoleCodes = new LinkedHashSet<>(user.roles());
        memberOf.forEach(group -> allRoleCodes.addAll(group.roles()));

        List<Role> resolvedRoles = allRoleCodes.isEmpty() ? List.of() : roles.findByCodes(user.tenantId(), allRoleCodes);
        Set<PermissionKey> permissions = resolvedRoles.stream()
                .flatMap(role -> role.permissions().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new EffectiveAccess(user.roles(), memberOf, resolvedRoles, permissions);
    }

    public record EffectiveAccess(Set<RoleCode> personalRoles, List<Group> groups, List<Role> roles,
                                  Set<PermissionKey> permissions) {

        public Set<String> roleCodes() {
            return roles.stream().map(r -> r.code().value()).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        public Set<String> authorities() {
            return permissions.stream().map(PermissionKey::authority)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
