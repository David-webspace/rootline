package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Email;
import dev.rootline.auth.application.domain.user.PasswordHash;
import dev.rootline.auth.application.domain.user.User;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.DomainEventOutbox;
import dev.rootline.auth.application.port.PasswordHasher;
import dev.rootline.auth.application.port.RoleRepository;
import dev.rootline.auth.application.port.UserRepository;
import dev.rootline.auth.application.shared.PasswordPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserCommandService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHasher hasher;
    private final DomainEventOutbox outbox;

    public UserCommandService(UserRepository users, RoleRepository roles, PasswordHasher hasher,
                              DomainEventOutbox outbox) {
        this.users = users;
        this.roles = roles;
        this.hasher = hasher;
        this.outbox = outbox;
    }

    public String create(TenantId tenantId, String username, String email, String rawPassword) {
        Username name = Username.of(username);
        Email address = Email.of(email);
        PasswordPolicy.validate(rawPassword);
        if (users.exists(tenantId, name)) {
            throw DomainException.conflict("USERNAME_TAKEN", "Username " + name + " already exists");
        }
        User user = User.register(tenantId, name, address, hasher.hash(rawPassword));
        persist(user);
        return user.username().value();
    }

    /**
     * 以已雜湊的密碼建立帳號（租戶開通時，tenant-service 只傳 bcrypt hash 過來）。
     * 已存在則不動作，讓重送的開通事件保持冪等。
     *
     * @return 是否真的新建
     */
    public boolean createWithPasswordHash(TenantId tenantId, String username, String email, PasswordHash hash) {
        Username name = Username.of(username);
        if (users.exists(tenantId, name)) {
            return false;
        }
        persist(User.register(tenantId, name, Email.of(email), hash));
        return true;
    }

    /**
     * @param requireCurrent 本人改自己的密碼時為 true，必須附上目前密碼；管理員重設他人密碼時為 false
     */
    public void changePassword(TenantId tenantId, String username, String currentPassword, String newPassword,
                               boolean requireCurrent) {
        User user = load(tenantId, username);
        if (requireCurrent && (currentPassword == null || !hasher.matches(currentPassword, user.passwordHash()))) {
            throw DomainException.violation("CURRENT_PASSWORD_MISMATCH", "Current password is incorrect");
        }
        PasswordPolicy.validate(newPassword);
        user.changePassword(hasher.hash(newPassword));
        persist(user);
    }

    public void changeEmail(TenantId tenantId, String username, String email) {
        User user = load(tenantId, username);
        user.changeEmail(Email.of(email));
        persist(user);
    }

    public void deactivate(TenantId tenantId, String username, String actor) {
        User user = load(tenantId, username);
        if (actor != null && user.username().value().equalsIgnoreCase(actor)) {
            throw DomainException.violation("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
        }
        user.deactivate();
        persist(user);
    }

    public void reactivate(TenantId tenantId, String username) {
        User user = load(tenantId, username);
        user.reactivate();
        persist(user);
    }

    public void unlock(TenantId tenantId, String username) {
        User user = load(tenantId, username);
        user.unlock();
        persist(user);
    }

    public void assignRole(TenantId tenantId, String username, String roleCode) {
        User user = load(tenantId, username);
        RoleCode code = RoleCode.of(roleCode);
        if (!roles.exists(tenantId, code)) {
            throw DomainException.notFound("ROLE_NOT_FOUND", "Role " + code + " not found");
        }
        user.assignRole(code);
        persist(user);
    }

    public void revokeRole(TenantId tenantId, String username, String roleCode) {
        User user = load(tenantId, username);
        user.revokeRole(RoleCode.of(roleCode));
        persist(user);
    }

    private User load(TenantId tenantId, String username) {
        Username name = Username.of(username);
        return users.findByUsername(tenantId, name)
                .orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "User " + name + " not found"));
    }

    private void persist(User user) {
        users.save(user);
        outbox.append(user.pullEvents());
    }
}
