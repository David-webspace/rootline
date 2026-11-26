package dev.rootline.userprofile.iface.rest;

import static dev.rootline.userprofile.iface.rest.Callers.tenant;

import dev.rootline.kernel.security.Authenticated;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import dev.rootline.userprofile.application.domain.profile.UserProfile.Details;
import dev.rootline.userprofile.application.domain.profile.UserProfile.Link;
import dev.rootline.userprofile.application.domain.profile.UserProfile.Preferences;
import dev.rootline.userprofile.application.service.ProfileService;
import dev.rootline.userprofile.application.service.ProfileService.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profiles")
class ProfileController {

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    record LinkRequest(String label, String url) {
    }

    record PreferencesRequest(boolean emailNotifications, boolean smsNotifications, String language) {
    }

    record UpdateRequest(String displayName, String jobTitle, String phone, String bio, List<LinkRequest> links,
                         @NotNull PreferencesRequest preferences) {
    }

    @GetMapping("/me")
    @Authenticated
    ApiResponse<View> me(CallerContext caller) {
        return ApiResponse.ok(profiles.get(tenant(caller), caller.userId()));
    }

    @PutMapping("/me")
    @Authenticated
    ApiResponse<View> update(CallerContext caller, @Valid @RequestBody UpdateRequest r) {
        Details details = new Details(r.displayName(), r.jobTitle(), r.phone(), r.bio(),
                r.links() == null ? List.of() : r.links().stream().map(l -> new Link(l.label(), l.url())).toList());
        Preferences preferences = new Preferences(r.preferences().emailNotifications(),
                r.preferences().smsNotifications(), r.preferences().language());
        return ApiResponse.ok("updated", profiles.updateMine(tenant(caller), caller.userId(), details, preferences));
    }

    @GetMapping("/{username}")
    @RequiresPermission(value = "IAM:USER_READ", selfPathVariable = "username")
    ApiResponse<View> get(CallerContext caller, @PathVariable String username) {
        return ApiResponse.ok(profiles.get(tenant(caller), username));
    }
}
