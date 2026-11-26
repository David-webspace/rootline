package dev.rootline.auth.infra.persistence;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "permission_dict")
class PermissionDictEntity {

    @EmbeddedId
    Key id;

    String permissionName;
    String description;
    long version;
    Instant updatedAt;

    protected PermissionDictEntity() {
    }

    PermissionDictEntity(Key id) {
        this.id = id;
    }

    @Embeddable
    static class Key implements Serializable {

        String tenantId;
        String systemCode;
        String permissionCode;

        protected Key() {
        }

        Key(String tenantId, String systemCode, String permissionCode) {
            this.tenantId = tenantId;
            this.systemCode = systemCode;
            this.permissionCode = permissionCode;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other
                    && Objects.equals(tenantId, other.tenantId)
                    && Objects.equals(systemCode, other.systemCode)
                    && Objects.equals(permissionCode, other.permissionCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, systemCode, permissionCode);
        }
    }
}
