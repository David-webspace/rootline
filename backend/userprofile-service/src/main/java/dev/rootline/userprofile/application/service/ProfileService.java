package dev.rootline.userprofile.application.service;

import dev.rootline.userprofile.application.domain.profile.UserProfile;
import dev.rootline.userprofile.application.domain.profile.UserProfile.Details;
import dev.rootline.userprofile.application.domain.profile.UserProfile.Preferences;
import dev.rootline.userprofile.application.domain.shared.TenantId;
import dev.rootline.userprofile.application.port.ProfilePorts.ProcessedEvents;
import dev.rootline.userprofile.application.port.ProfilePorts.ProfileRepository;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

    public static final String USER_CONSUMER = "profile-user-projector";
    public static final String KYC_CONSUMER = "profile-kyc-projector";

    private final ProfileRepository profiles;
    private final ProcessedEvents processed;

    public ProfileService(ProfileRepository profiles, ProcessedEvents processed) {
        this.profiles = profiles;
        this.processed = processed;
    }

    /** auth-service 的使用者事件 → 身分資訊。 */
    public boolean applyUserEvent(IncomingEvent event) {
        if (!processed.markProcessed(event.eventId(), USER_CONSUMER)) {
            return false;
        }
        Map<String, Object> p = event.payload();
        String email = p.get("email") instanceof String s ? s : null;
        String status = switch (event.eventType()) {
            case "UserRegistered", "UserReactivated", "UserUnlocked" -> "ACTIVE";
            case "UserDeactivated" -> "DEACTIVATED";
            case "UserLocked" -> "LOCKED";
            case "UserEmailChanged" -> null;
            default -> "IGNORE";
        };
        if ("IGNORE".equals(status)) {
            return false;
        }
        change(TenantId.of(event.tenantId()), text(p, "username"), profile -> profile.syncIdentity(email, status));
        return true;
    }

    /** kyc-service 的狀態事件 → 實名狀態。 */
    public boolean applyKycEvent(IncomingEvent event) {
        if (!processed.markProcessed(event.eventId(), KYC_CONSUMER) || !"KycStatusChanged".equals(event.eventType())) {
            return false;
        }
        String newStatus = text(event.payload(), "newStatus");
        change(TenantId.of(event.tenantId()), text(event.payload(), "userId"), profile -> profile.syncKycStatus(newStatus));
        return true;
    }

    public View updateMine(TenantId tenantId, String username, Details details, Preferences preferences) {
        return View.of(change(tenantId, username, profile -> profile.update(details, preferences)));
    }

    @Transactional(readOnly = true)
    public View get(TenantId tenantId, String username) {
        return View.of(profiles.find(tenantId, username).orElseGet(() -> UserProfile.blank(tenantId, username)));
    }

    private UserProfile change(TenantId tenantId, String username, Consumer<UserProfile> action) {
        var existing = profiles.find(tenantId, username.toLowerCase(java.util.Locale.ROOT));
        UserProfile profile = existing.orElseGet(() -> UserProfile.blank(tenantId, username));
        action.accept(profile);
        profiles.save(profile, existing.isEmpty());
        return profile;
    }

    private static String text(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("Event payload is missing '" + field + "'");
        }
        return s;
    }

    public record IncomingEvent(UUID eventId, String eventType, String tenantId, Map<String, Object> payload) {
    }

    public record View(String username, String email, String accountStatus, String kycStatus, Details details,
                       Preferences preferences) {

        static View of(UserProfile p) {
            return new View(p.username(), p.email(), p.accountStatus(), p.kycStatus(), p.details(), p.preferences());
        }
    }
}
