package dev.rootline.auth.iface.rest;

import static dev.rootline.auth.iface.rest.Callers.tenant;

import dev.rootline.auth.application.service.RoleCommandService;
import dev.rootline.auth.application.service.RoleQueryService;
import dev.rootline.auth.application.shared.Views.RoleView;
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
@RequestMapping("/api/roles")
class RoleController {

    private final RoleCommandService commands;
    private final RoleQueryService queries;

    RoleController(RoleCommandService commands, RoleQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @RequiresPermission(IamPermissions.ROLE_WRITE)
    ResponseEntity<ApiResponse<String>> create(CallerContext caller, @Valid @RequestBody Requests.CreateRole request) {
        String code = commands.create(tenant(caller), request.roleCode(), request.roleName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created", code));
    }

    @GetMapping
    @RequiresPermission(IamPermissions.ROLE_READ)
    ApiResponse<List<RoleView>> list(CallerContext caller) {
        return ApiResponse.ok(queries.list(tenant(caller)));
    }

    @GetMapping("/{roleCode}")
    @RequiresPermission(IamPermissions.ROLE_READ)
    ApiResponse<RoleView> get(CallerContext caller, @PathVariable String roleCode) {
        return ApiResponse.ok(queries.get(tenant(caller), roleCode));
    }

    @PutMapping("/{roleCode}/name")
    @RequiresPermission(IamPermissions.ROLE_WRITE)
    ApiResponse<Void> rename(CallerContext caller, @PathVariable String roleCode,
                             @Valid @RequestBody Requests.Rename request) {
        commands.rename(tenant(caller), roleCode, request.name());
        return ApiResponse.ok("renamed", null);
    }

    @PostMapping("/{roleCode}/permissions")
    @RequiresPermission(IamPermissions.ROLE_WRITE)
    ApiResponse<Void> grant(CallerContext caller, @PathVariable String roleCode,
                            @Valid @RequestBody Requests.GrantPermission request) {
        commands.grantPermission(tenant(caller), roleCode, request.systemCode(), request.permissionCode());
        return ApiResponse.ok("permission granted", null);
    }

    @DeleteMapping("/{roleCode}/permissions/{systemCode}/{permissionCode}")
    @RequiresPermission(IamPermissions.ROLE_WRITE)
    ApiResponse<Void> revoke(CallerContext caller, @PathVariable String roleCode, @PathVariable String systemCode,
                             @PathVariable String permissionCode) {
        commands.revokePermission(tenant(caller), roleCode, systemCode, permissionCode);
        return ApiResponse.ok("permission revoked", null);
    }
}
