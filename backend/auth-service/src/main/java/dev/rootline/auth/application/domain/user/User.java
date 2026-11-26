package dev.rootline.auth.application.domain.user;

import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.AggregateRoot;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.UserEvent.UserDeactivated;
import dev.rootline.auth.application.domain.user.UserEvent.UserEmailChanged;
import dev.rootline.auth.application.domain.user.UserEvent.UserLocked;
import dev.rootline.auth.application.domain.user.UserEvent.UserPasswordChanged;
import dev.rootline.auth.application.domain.user.UserEvent.UserReactivated;
import dev.rootline.auth.application.domain.user.UserEvent.UserRegistered;
import dev.rootline.auth.application.domain.user.UserEvent.UserRoleAssigned;
import dev.rootline.auth.application.domain.user.UserEvent.UserRoleRevoked;
import dev.rootline.auth.application.domain.user.UserEvent.UserUnlocked;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 使用者聚合根。不變量：
 * <ul>
 *   <li>停用中的帳號不能改資料、不能被指派角色</li>
 *   <li>連續登入失敗 {@value #MAX_FAILED_LOGIN_ATTEMPTS} 次自動鎖定</li>
 *   <li>角色以 {@link RoleCode} 參照，不持有 Role 聚合本身</li>
 * </ul>
 */
public class User extends AggregateRoot {

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private final UUID id;
    private final TenantId tenantId;
    private final Username username;
    private Email email;
    private PasswordHash passwordHash;
    private UserStatus status;
    private int failedLoginAttempts;
    private final Set<RoleCode> roles;

    private User(UUID id, TenantId tenantId, Username username, Email email, PasswordHash passwordHash,
                 UserStatus status, int failedLoginAttempts, Set<RoleCode> roles) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.username = Objects.requireNonNull(username);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.status = Objects.requireNonNull(status);
        this.failedLoginAttempts = failedLoginAttempts;
        this.roles = new LinkedHashSet<>(roles);
    }

    public static User register(TenantId tenantId, Username username, Email email, PasswordHash passwordHash) {
        User user = new User(UUID.randomUUID(), tenantId, username, email, passwordHash, UserStatus.ACTIVE, 0,
                Set.of());
        user.raise(new UserRegistered(UUID.randomUUID(), Instant.now(), tenantId.value(), user.id.toString(),
                username.value(), email.value()));
        return user;
    }

    /** 從儲存層還原，不產生事件。 */
    public static User rehydrate(UUID id, TenantId tenantId, Username username, Email email,
                                 PasswordHash passwordHash, UserStatus status, int failedLoginAttempts,
                                 Set<RoleCode> roles) {
        return new User(id, tenantId, username, email, passwordHash, status, failedLoginAttempts, roles);
    }

    public void changeEmail(Email newEmail) {
        requireNotDeactivated("change email");
        if (email.equals(newEmail)) {
            return;
        }
        email = newEmail;
        raise(new UserEmailChanged(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                username.value(), newEmail.value()));
    }

    public void changePassword(PasswordHash newHash) {
        requireNotDeactivated("change password");
        passwordHash = Objects.requireNonNull(newHash);
        raise(new UserPasswordChanged(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                username.value()));
    }

    /**
     * 記錄一次密碼錯誤。只有 ACTIVE 帳號會累計；達到上限時鎖定。
     *
     * @return 這次是否因此被鎖定
     */
    public boolean recordFailedLogin() {
        if (status != UserStatus.ACTIVE) {
            return false;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            status = UserStatus.LOCKED;
            raise(new UserLocked(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    username.value(), failedLoginAttempts));
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin() {
        if (status != UserStatus.ACTIVE) {
            throw DomainException.violation("USER_NOT_ACTIVE", "User " + username + " is " + status);
        }
        failedLoginAttempts = 0;
    }

    public void unlock() {
        if (status != UserStatus.LOCKED) {
            throw DomainException.violation("USER_NOT_LOCKED", "User " + username + " is not locked");
        }
        status = UserStatus.ACTIVE;
        failedLoginAttempts = 0;
        raise(new UserUnlocked(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                username.value()));
    }

    public void deactivate() {
        if (status == UserStatus.DEACTIVATED) {
            throw DomainException.violation("USER_ALREADY_DEACTIVATED", "User " + username + " is already deactivated");
        }
        status = UserStatus.DEACTIVATED;
        raise(new UserDeactivated(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                username.value()));
    }

    public void reactivate() {
        if (status != UserStatus.DEACTIVATED) {
            throw DomainException.violation("USER_NOT_DEACTIVATED", "User " + username + " is not deactivated");
        }
        status = UserStatus.ACTIVE;
        failedLoginAttempts = 0;
        raise(new UserReactivated(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                username.value()));
    }

    public void assignRole(RoleCode roleCode) {
        requireNotDeactivated("assign role");
        if (roles.add(Objects.requireNonNull(roleCode))) {
            raise(new UserRoleAssigned(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    username.value(), roleCode.value()));
        }
    }

    public void revokeRole(RoleCode roleCode) {
        if (roles.remove(roleCode)) {
            raise(new UserRoleRevoked(UUID.randomUUID(), Instant.now(), tenantId.value(), id.toString(),
                    username.value(), roleCode.value()));
        }
    }

    private void requireNotDeactivated(String action) {
        if (status == UserStatus.DEACTIVATED) {
            throw DomainException.violation("USER_DEACTIVATED", "Cannot " + action + " for deactivated user " + username);
        }
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Username username() {
        return username;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
        return status;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Set<RoleCode> roles() {
        return Collections.unmodifiableSet(roles);
    }
}
