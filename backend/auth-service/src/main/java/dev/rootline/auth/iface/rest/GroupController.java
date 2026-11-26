package dev.rootline.auth.iface.rest;

import static dev.rootline.auth.iface.rest.Callers.tenant;

import dev.rootline.auth.application.service.GroupCommandService;
import dev.rootline.auth.application.service.GroupQueryService;
import dev.rootline.auth.application.shared.Views.GroupView;
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
@RequestMapping("/api/groups")
class GroupController {

    private final GroupCommandService commands;
    private final GroupQueryService queries;

    GroupController(GroupCommandService commands, GroupQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @RequiresPermission(IamPermissions.GROUP_WRITE)
    ResponseEntity<ApiResponse<String>> create(CallerContext caller, @Valid @RequestBody Requests.CreateGroup request) {
        String code = commands.create(tenant(caller), request.groupCode(), request.groupName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created", code));
    }

    @GetMapping
    @RequiresPermission(IamPermissions.GROUP_READ)
    ApiResponse<List<GroupView>> list(CallerContext caller) {
        return ApiResponse.ok(queries.list(tenant(caller)));
    }

    @GetMapping("/{groupCode}")
    @RequiresPermission(IamPermissions.GROUP_READ)
    ApiResponse<GroupView> get(CallerContext caller, @PathVariable String groupCode) {
        return ApiResponse.ok(queries.get(tenant(caller), groupCode));
    }

    @PutMapping("/{groupCode}/name")
    @RequiresPermission(IamPermissions.GROUP_WRITE)
    ApiResponse<Void> rename(CallerContext caller, @PathVariable String groupCode,
                             @Valid @RequestBody Requests.Rename request) {
        commands.rename(tenant(caller), groupCode, request.name());
        return ApiResponse.ok("renamed", null);
    }

    @PostMapping("/{groupCode}/members/{username}")
    @RequiresPermission(IamPermissions.GROUP_WRITE)
    ApiResponse<Void> addMember(CallerContext caller, @PathVariable String groupCode, @PathVariable String username) {
        commands.addMember(tenant(caller), groupCode, username);
        return ApiResponse.ok("member added", null);
    }

    @DeleteMapping("/{groupCode}/members/{username}")
    @RequiresPermission(IamPermissions.GROUP_WRITE)
    ApiResponse<Void> removeMember(CallerContext caller, @PathVariable String groupCode,
                                   @PathVariable String username) {
        commands.removeMember(tenant(caller), groupCode, username);
        return ApiResponse.ok("member removed", null);
    }

    @PostMapping("/{groupCode}/roles/{roleCode}")
    @RequiresPermission(IamPermissions.GROUP_WRITE)
    ApiResponse<Void> assignRole(CallerContext caller, @PathVariable String groupCode, @PathVariable String roleCode) {
        commands.assignRole(tenant(caller), groupCode, roleCode);
        return ApiResponse.ok("role assigned", null);
    }

    @DeleteMapping("/{groupCode}/roles/{roleCode}")
    @RequiresPermission(IamPermissions.GROUP_WRITE)
    ApiResponse<Void> revokeRole(CallerContext caller, @PathVariable String groupCode, @PathVariable String roleCode) {
        commands.revokeRole(tenant(caller), groupCode, roleCode);
        return ApiResponse.ok("role revoked", null);
    }
}
