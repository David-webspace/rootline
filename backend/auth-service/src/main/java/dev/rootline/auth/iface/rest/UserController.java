package dev.rootline.auth.iface.rest;

import static dev.rootline.auth.iface.rest.Callers.isSelf;
import static dev.rootline.auth.iface.rest.Callers.tenant;

import dev.rootline.auth.application.service.UserCommandService;
import dev.rootline.auth.application.service.UserQueryService;
import dev.rootline.auth.application.shared.Views.PermissionContextView;
import dev.rootline.auth.application.shared.Views.UserView;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserCommandService commands;
    private final UserQueryService queries;

    UserController(UserCommandService commands, UserQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @RequiresPermission(IamPermissions.USER_WRITE)
    ResponseEntity<ApiResponse<String>> create(CallerContext caller, @Valid @RequestBody Requests.CreateUser request) {
        String username = commands.create(tenant(caller), request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created", username));
    }

    @GetMapping
    @RequiresPermission(IamPermissions.USER_READ)
    ApiResponse<List<UserView>> list(CallerContext caller) {
        return ApiResponse.ok(queries.list(tenant(caller)));
    }

    @GetMapping("/{username}")
    @RequiresPermission(value = IamPermissions.USER_READ, selfPathVariable = "username")
    ApiResponse<UserView> get(CallerContext caller, @PathVariable String username) {
        return ApiResponse.ok(queries.get(tenant(caller), username));
    }

    @GetMapping("/{username}/permissions-context")
    @RequiresPermission(value = IamPermissions.USER_READ, selfPathVariable = "username")
    ApiResponse<PermissionContextView> permissionContext(CallerContext caller, @PathVariable String username) {
        return ApiResponse.ok(queries.permissionContext(tenant(caller), username));
    }

    /** 本人改密碼必須附目前密碼；有 USER_WRITE 的管理員可直接重設他人密碼。 */
    @PutMapping("/{username}/password")
    @RequiresPermission(value = IamPermissions.USER_WRITE, selfPathVariable = "username")
    ApiResponse<Void> changePassword(CallerContext caller, @PathVariable String username,
                                     @Valid @RequestBody Requests.ChangePassword request) {
        commands.changePassword(tenant(caller), username, request.currentPassword(), request.newPassword(),
                isSelf(caller, username));
        return ApiResponse.ok("password changed", null);
    }

    @PutMapping("/{username}/profile")
    @RequiresPermission(value = IamPermissions.USER_WRITE, selfPathVariable = "username")
    ApiResponse<Void> updateProfile(CallerContext caller, @PathVariable String username,
                                    @Valid @RequestBody Requests.UpdateProfile request) {
        commands.changeEmail(tenant(caller), username, request.email());
        return ApiResponse.ok("profile updated", null);
    }

    /** 邏輯刪除（停用），可用 reactivate 復權。 */
    @DeleteMapping("/{username}")
    @RequiresPermission(IamPermissions.USER_WRITE)
    ApiResponse<Void> deactivate(CallerContext caller, @PathVariable String username) {
        commands.deactivate(tenant(caller), username, caller.userId());
        return ApiResponse.ok("deactivated", null);
    }

    @PostMapping("/{username}/reactivate")
    @RequiresPermission(IamPermissions.USER_WRITE)
    ApiResponse<Void> reactivate(CallerContext caller, @PathVariable String username) {
        commands.reactivate(tenant(caller), username);
        return ApiResponse.ok("reactivated", null);
    }

    @PostMapping("/{username}/unlock")
    @RequiresPermission(IamPermissions.USER_WRITE)
    ApiResponse<Void> unlock(CallerContext caller, @PathVariable String username) {
        commands.unlock(tenant(caller), username);
        return ApiResponse.ok("unlocked", null);
    }

    @PostMapping("/{username}/roles/{roleCode}")
    @RequiresPermission(IamPermissions.USER_WRITE)
    ApiResponse<Void> assignRole(CallerContext caller, @PathVariable String username, @PathVariable String roleCode) {
        commands.assignRole(tenant(caller), username, roleCode);
        return ApiResponse.ok("role assigned", null);
    }

    @DeleteMapping("/{username}/roles/{roleCode}")
    @RequiresPermission(IamPermissions.USER_WRITE)
    ApiResponse<Void> revokeRole(CallerContext caller, @PathVariable String username, @PathVariable String roleCode) {
        commands.revokeRole(tenant(caller), username, roleCode);
        return ApiResponse.ok("role revoked", null);
    }
}
