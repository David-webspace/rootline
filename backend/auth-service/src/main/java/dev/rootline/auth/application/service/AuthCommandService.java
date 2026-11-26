package dev.rootline.auth.application.service;

import dev.rootline.auth.application.domain.shared.DomainException;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.User;
import dev.rootline.auth.application.domain.user.UserStatus;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.DomainEventOutbox;
import dev.rootline.auth.application.port.PasswordHasher;
import dev.rootline.auth.application.port.TenantDirectory;
import dev.rootline.auth.application.port.TokenIssuer;
import dev.rootline.auth.application.port.TokenIssuer.IssuedToken;
import dev.rootline.auth.application.port.UserRepository;
import dev.rootline.auth.application.service.EffectiveAccessResolver.EffectiveAccess;
import dev.rootline.auth.application.shared.AccountLockedException;
import dev.rootline.auth.application.shared.AuthenticationFailedException;
import dev.rootline.auth.application.shared.Views.LoginResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthCommandService {

    private final UserRepository users;
    private final TenantDirectory tenants;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private final EffectiveAccessResolver access;
    private final DomainEventOutbox outbox;
    private final UserCommandService userCommands;

    public AuthCommandService(UserRepository users, TenantDirectory tenants, PasswordHasher hasher,
                              TokenIssuer tokens, EffectiveAccessResolver access, DomainEventOutbox outbox,
                              UserCommandService userCommands) {
        this.users = users;
        this.tenants = tenants;
        this.hasher = hasher;
        this.tokens = tokens;
        this.access = access;
        this.outbox = outbox;
        this.userCommands = userCommands;
    }

    /**
     * 登入。失敗次數要能寫回資料庫，所以 {@link AuthenticationFailedException} 不觸發 rollback。
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public LoginResult login(String tenantCode, String username, String rawPassword) {
        Optional<TenantId> tenantId = parse(() -> TenantId.of(tenantCode));
        Optional<Username> name = parse(() -> Username.of(username));
        String password = rawPassword == null ? "" : rawPassword;

        Optional<User> found = tenantId.isPresent() && name.isPresent() && tenants.isActive(tenantId.get())
                ? users.findByUsername(tenantId.get(), name.get())
                : Optional.empty();

        if (found.isEmpty() || found.get().status() == UserStatus.DEACTIVATED) {
            hasher.burnEquivalentTime(password);
            throw new AuthenticationFailedException();
        }

        User user = found.get();
        if (!hasher.matches(password, user.passwordHash())) {
            user.recordFailedLogin();
            persist(user);
            throw new AuthenticationFailedException();
        }
        if (user.status() == UserStatus.LOCKED) {
            throw new AccountLockedException();
        }

        user.recordSuccessfulLogin();
        persist(user);

        EffectiveAccess effective = access.resolve(user);
        IssuedToken token = tokens.issue(user.tenantId(), user.username(), effective.roleCodes(),
                effective.authorities());
        return new LoginResult(token.token(), token.expiresAt(), user.tenantId().value(), user.username().value(),
                List.copyOf(effective.roleCodes()), List.copyOf(effective.authorities()));
    }

    /** 自助註冊：租戶必須存在且為啟用狀態；新帳號沒有任何角色，由管理員授權。 */
    @Transactional
    public String register(String tenantCode, String username, String email, String rawPassword) {
        TenantId tenantId = TenantId.of(tenantCode);
        if (!tenants.isActive(tenantId)) {
            throw DomainException.violation("TENANT_NOT_AVAILABLE", "Tenant " + tenantId + " is not available");
        }
        return userCommands.create(tenantId, username, email, rawPassword);
    }

    public Map<String, Object> publicJwks() {
        return tokens.publicJwks();
    }

    private void persist(User user) {
        users.save(user);
        outbox.append(user.pullEvents());
    }

    private static <T> Optional<T> parse(Supplier<T> parser) {
        try {
            return Optional.of(parser.get());
        } catch (DomainException e) {
            return Optional.empty();
        }
    }
}
