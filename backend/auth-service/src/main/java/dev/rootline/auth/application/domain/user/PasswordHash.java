package dev.rootline.auth.application.domain.user;

import dev.rootline.auth.application.domain.shared.DomainException;

/** 已雜湊的密碼。domain 永遠不碰明文。 */
public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw DomainException.invalid("PASSWORD_HASH_INVALID", "Password hash is required");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[***]";
    }
}
