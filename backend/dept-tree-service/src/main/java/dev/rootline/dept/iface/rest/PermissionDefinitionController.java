package dev.rootline.dept.iface.rest;

import static dev.rootline.dept.iface.rest.Callers.tenant;

import dev.rootline.dept.application.service.PermissionDefinitionService;
import dev.rootline.dept.application.service.PermissionDefinitionService.View;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments/permissions")
class PermissionDefinitionController {

    private final PermissionDefinitionService service;

    PermissionDefinitionController(PermissionDefinitionService service) {
        this.service = service;
    }

    record Define(@NotBlank String systemCode, @NotBlank String permissionCode, @NotBlank String name,
                  String description) {
    }

    record Update(@NotBlank String name, String description) {
    }

    @GetMapping
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<List<View>> list(CallerContext caller) {
        return ApiResponse.ok(service.list(tenant(caller)));
    }

    @PostMapping
    @RequiresPermission(DeptPermissions.PERMISSION_WRITE)
    ResponseEntity<ApiResponse<View>> define(CallerContext caller, @Valid @RequestBody Define request) {
        View view = service.define(tenant(caller), request.systemCode(), request.permissionCode(), request.name(),
                request.description(), caller.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("defined", view));
    }

    @PutMapping("/{systemCode}/{permissionCode}")
    @RequiresPermission(DeptPermissions.PERMISSION_WRITE)
    ApiResponse<View> update(CallerContext caller, @PathVariable String systemCode, @PathVariable String permissionCode,
                             @Valid @RequestBody Update request) {
        return ApiResponse.ok("updated", service.update(tenant(caller), systemCode, permissionCode, request.name(),
                request.description(), caller.userId()));
    }
}
