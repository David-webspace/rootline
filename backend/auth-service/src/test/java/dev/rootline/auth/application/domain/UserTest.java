package dev.rootline.auth.application.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.DomainEvent;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Email;
import dev.rootline.auth.application.domain.user.PasswordHash;
import dev.rootline.auth.application.domain.user.User;
import dev.rootline.auth.application.domain.user.UserEvent.UserLocked;
import dev.rootline.auth.application.domain.user.UserEvent.UserRegistered;
import dev.rootline.auth.application.domain.user.UserEvent.UserRoleAssigned;
import dev.rootline.auth.application.domain.user.UserStatus;
import dev.rootline.auth.application.domain.user.Username;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserTest {

    private static User newUser() {
        User user = User.register(TenantId.of("northwind"), Username.of("Mia.Chen"), Email.of("Mia@Northwind.example"),
                new PasswordHash("$2a$hash"));
        user.pullEvents();
        return user;
    }

    @Test
    void registerNormalizesAndRaisesEvent() {
        User user = User.register(TenantId.of("Northwind"), Username.of("Mia.Chen"),
                Email.of("Mia@Northwind.example"), new PasswordHash("$2a$hash"));

        assertThat(user.username().value()).isEqualTo("mia.chen");
        assertThat(user.email().value()).isEqualTo("mia@northwind.example");
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);

        List<DomainEvent> events = user.pullEvents();
        assertThat(events).singleElement().isInstanceOf(UserRegistered.class);
        assertThat(events.getFirst().tenantId()).isEqualTo("northwind");
        assertThat(user.pullEvents()).isEmpty();
    }

    @Test
    void locksAfterMaxFailedAttempts() {
        User user = newUser();
        for (int i = 1; i < User.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            assertThat(user.recordFailedLogin()).isFalse();
        }
        assertThat(user.recordFailedLogin()).isTrue();
        assertThat(user.status()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.pullEvents()).singleElement().isInstanceOf(UserLocked.class);

        // 已鎖定後不再累計，也不重複發事件
        assertThat(user.recordFailedLogin()).isFalse();
        assertThat(user.pullEvents()).isEmpty();
    }

    @Test
    void successfulLoginResetsCounter() {
        User user = newUser();
        user.recordFailedLogin();
        user.recordFailedLogin();
        user.recordSuccessfulLogin();
        assertThat(user.failedLoginAttempts()).isZero();
    }

    @Test
    void unlockRestoresActive() {
        User user = newUser();
        for (int i = 0; i < User.MAX_FAILED_LOGIN_ATTEMPTS; i++) {
            user.recordFailedLogin();
        }
        user.unlock();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.failedLoginAttempts()).isZero();
    }

    @Test
    void unlockRequiresLockedState() {
        assertThatThrownBy(() -> newUser().unlock())
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("USER_NOT_LOCKED");
    }

    @Test
    void deactivatedUserCannotBeChangedUntilReactivated() {
        User user = newUser();
        user.deactivate();

        assertThatThrownBy(() -> user.changeEmail(Email.of("new@northwind.example")))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.assignRole(RoleCode.of("HR_VIEWER")))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(user::deactivate).isInstanceOf(DomainException.class);

        user.reactivate();
        user.changeEmail(Email.of("new@northwind.example"));
        assertThat(user.email().value()).isEqualTo("new@northwind.example");
    }

    @Test
    void assigningSameRoleTwiceIsIdempotent() {
        User user = newUser();
        user.assignRole(RoleCode.of("hr_viewer"));
        user.assignRole(RoleCode.of("HR_VIEWER"));
        assertThat(user.roles()).containsExactly(RoleCode.of("HR_VIEWER"));
        assertThat(user.pullEvents()).singleElement().isInstanceOf(UserRoleAssigned.class);
    }

    @Test
    void invalidValueObjectsAreRejected() {
        assertThatThrownBy(() -> Username.of("a")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Username.of("has space")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Email.of("not-an-email")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> TenantId.of("UPPER_case!")).isInstanceOf(DomainException.class);
    }

    @Test
    void passwordHashIsNeverPrinted() {
        assertThat(new PasswordHash("$2a$secret").toString()).doesNotContain("secret");
    }
}
