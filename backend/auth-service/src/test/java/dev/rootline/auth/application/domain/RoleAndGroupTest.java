package dev.rootline.auth.application.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rootline.auth.application.domain.group.Group;
import dev.rootline.auth.application.domain.group.GroupCode;
import dev.rootline.auth.application.domain.group.GroupEvent.GroupMemberAdded;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.role.Role;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.role.RoleEvent.RolePermissionGranted;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleAndGroupTest {

    private static final TenantId TENANT = TenantId.of("northwind");

    @Test
    void customRoleGrantsAndRevokesPermissions() {
        Role role = Role.createCustom(TENANT, RoleCode.of("HR_VIEWER"), "人資檢視");
        role.pullEvents();

        role.grant(PermissionKey.parse("IAM:USER_READ"));
        role.grant(PermissionKey.parse("iam:user_read"));
        assertThat(role.permissions()).containsExactly(new PermissionKey("IAM", "USER_READ"));
        assertThat(role.pullEvents()).singleElement().isInstanceOf(RolePermissionGranted.class);

        role.revoke(PermissionKey.parse("IAM:USER_READ"));
        assertThat(role.permissions()).isEmpty();
    }

    @Test
    void systemRootRoleIsImmutable() {
        Role admin = Role.createSystemRoot(TENANT, RoleCode.of("TENANT_ADMIN"), "租戶管理員",
                Set.of(PermissionKey.SUPER_ADMIN));

        assertThatThrownBy(() -> admin.rename("Hacked"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("SYSTEM_ROLE_IMMUTABLE");
        assertThatThrownBy(() -> admin.revoke(PermissionKey.SUPER_ADMIN)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> admin.grant(PermissionKey.parse("IAM:USER_READ"))).isInstanceOf(DomainException.class);
    }

    @Test
    void permissionKeyRules() {
        assertThat(PermissionKey.parse("*:ADMIN_ALL")).isEqualTo(PermissionKey.SUPER_ADMIN);
        assertThat(PermissionKey.parse("IAM:ADMIN_ALL").isWildcard()).isTrue();
        assertThat(PermissionKey.parse("IAM:USER_READ").isWildcard()).isFalse();
        assertThatThrownBy(() -> PermissionKey.parse("*:USER_READ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PermissionKey.parse("no-colon")).isInstanceOf(DomainException.class);
    }

    @Test
    void groupMembershipIsIdempotent() {
        Group group = Group.create(TENANT, GroupCode.of("finance_team"), "財務部");
        group.pullEvents();

        group.addMember(Username.of("mia"));
        group.addMember(Username.of("MIA"));
        assertThat(group.members()).containsExactly(Username.of("mia"));
        assertThat(group.pullEvents()).singleElement().isInstanceOf(GroupMemberAdded.class);

        group.removeMember(Username.of("leo"));
        assertThat(group.pullEvents()).isEmpty();
    }

    @Test
    void codesAreNormalizedToUpperCase() {
        assertThat(GroupCode.of("finance_team").value()).isEqualTo("FINANCE_TEAM");
        assertThatThrownBy(() -> RoleCode.of("1BAD")).isInstanceOf(DomainException.class);
    }
}
