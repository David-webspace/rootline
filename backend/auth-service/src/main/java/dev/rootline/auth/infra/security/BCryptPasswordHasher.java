package dev.rootline.auth.infra.security;

import dev.rootline.auth.application.domain.user.PasswordHash;
import dev.rootline.auth.application.port.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;
    /** 帳號不存在時拿來比對的假 hash，成本與真的一樣。 */
    private final String dummyHash;

    BCryptPasswordHasher(@Value("${rootline.security.bcrypt-strength:12}") int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
        this.dummyHash = encoder.encode("rootline-timing-equalizer");
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        return rawPassword != null && encoder.matches(rawPassword, hash.value());
    }

    @Override
    public void burnEquivalentTime(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }
}
