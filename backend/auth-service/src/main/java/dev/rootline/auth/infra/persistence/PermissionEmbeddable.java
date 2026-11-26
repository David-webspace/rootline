package dev.rootline.auth.infra.persistence;

import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
class PermissionEmbeddable {

    String systemCode;
    String permissionCode;

    protected PermissionEmbeddable() {
    }

    PermissionEmbeddable(String systemCode, String permissionCode) {
        this.systemCode = systemCode;
        this.permissionCode = permissionCode;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PermissionEmbeddable other
                && Objects.equals(systemCode, other.systemCode)
                && Objects.equals(permissionCode, other.permissionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemCode, permissionCode);
    }
}
