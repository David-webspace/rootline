package dev.rootline.auth.infra.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** users 表的持久化模型。只存在 infra 層，domain 的 User 不認識它。 */
@Entity
@Table(name = "users")
class UserEntity {

    @Id
    UUID id;

    String tenantId;
    String username;
    String email;
    String passwordHash;
    String status;
    int failedLoginAttempts;

    @Version
    long version;

    @Column(updatable = false)
    Instant createdAt;
    Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_code")
    Set<String> roles = new HashSet<>();

    protected UserEntity() {
    }

    UserEntity(UUID id) {
        this.id = id;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
