package dev.rootline.userprofile.application.port;

import dev.rootline.userprofile.application.domain.profile.UserProfile;
import dev.rootline.userprofile.application.domain.shared.TenantId;
import java.util.Optional;
import java.util.UUID;

public final class ProfilePorts {

    private ProfilePorts() {
    }

    public interface ProfileRepository {

        Optional<UserProfile> find(TenantId tenantId, String username);

        /** 新增或樂觀鎖更新（新建的 profile 版本為 0）。 */
        void save(UserProfile profile, boolean isNew);
    }

    public interface ProcessedEvents {

        boolean markProcessed(UUID eventId, String consumer);
    }
}
