package dev.rootline.auth.infra.persistence;

import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Email;
import dev.rootline.auth.application.domain.user.PasswordHash;
import dev.rootline.auth.application.domain.user.User;
import dev.rootline.auth.application.domain.user.UserStatus;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserRepository implements UserRepository {

    private final UserJpaRepository jpa;

    JpaUserRepository(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findByUsername(TenantId tenantId, Username username) {
        return jpa.findByTenantIdAndUsername(tenantId.value(), username.value()).map(JpaUserRepository::toDomain);
    }

    @Override
    public boolean exists(TenantId tenantId, Username username) {
        return jpa.existsByTenantIdAndUsername(tenantId.value(), username.value());
    }

    @Override
    public List<User> findAll(TenantId tenantId) {
        return jpa.findByTenantId(tenantId.value()).stream().map(JpaUserRepository::toDomain).toList();
    }

    /** 先取回同一個 managed entity 再覆寫欄位，讓 @Version 樂觀鎖能擋住併發修改。 */
    @Override
    public void save(User user) {
        UserEntity entity = jpa.findById(user.id()).orElseGet(() -> new UserEntity(user.id()));
        entity.tenantId = user.tenantId().value();
        entity.username = user.username().value();
        entity.email = user.email().value();
        entity.passwordHash = user.passwordHash().value();
        entity.status = user.status().name();
        entity.failedLoginAttempts = user.failedLoginAttempts();
        entity.roles.clear();
        user.roles().forEach(role -> entity.roles.add(role.value()));
        jpa.save(entity);
    }

    private static User toDomain(UserEntity e) {
        return User.rehydrate(e.id, TenantId.of(e.tenantId), Username.of(e.username), Email.of(e.email),
                new PasswordHash(e.passwordHash), UserStatus.valueOf(e.status), e.failedLoginAttempts,
                e.roles.stream().map(RoleCode::of).collect(Collectors.toSet()));
    }
}
