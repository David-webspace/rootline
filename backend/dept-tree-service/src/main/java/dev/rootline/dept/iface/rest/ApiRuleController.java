package dev.rootline.dept.iface.rest;

import static dev.rootline.dept.iface.rest.Callers.tenant;

import dev.rootline.dept.application.port.DepartmentReadModel.Page;
import dev.rootline.dept.application.service.ApiRuleService;
import dev.rootline.dept.application.service.ApiRuleService.View;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 動態 API 規則（第二軌權限）的管理介面。 */
@RestController
@RequestMapping("/api/departments/api-rules")
@RequiresPermission(DeptPermissions.API_RULE_WRITE)
class ApiRuleController {

    private final ApiRuleService service;

    ApiRuleController(ApiRuleService service) {
        this.service = service;
    }

    record RuleRequest(@NotBlank String method, @NotBlank String pathPattern, @NotBlank String requiredPermission,
                       String description) {
    }

    record StatusRequest(@NotNull Boolean enabled) {
    }

    @GetMapping
    ApiResponse<Page<View>> page(CallerContext caller, @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.page(tenant(caller), page, size));
    }

    @PostMapping
    ResponseEntity<ApiResponse<View>> create(CallerContext caller, @Valid @RequestBody RuleRequest request) {
        View view = service.create(tenant(caller), request.method(), request.pathPattern(),
                request.requiredPermission(), request.description(), caller.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created", view));
    }

    @PutMapping("/{id}")
    ApiResponse<View> update(CallerContext caller, @PathVariable UUID id, @Valid @RequestBody RuleRequest request) {
        return ApiResponse.ok("updated", service.update(tenant(caller), id, request.method(), request.pathPattern(),
                request.requiredPermission(), request.description(), caller.userId()));
    }

    @PatchMapping("/{id}/status")
    ApiResponse<View> status(CallerContext caller, @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok("updated", service.setEnabled(tenant(caller), id, request.enabled(), caller.userId()));
    }

    /** 以資料庫為準重新寫一次 Redis（Redis 被清空或懷疑不一致時用）。 */
    @PostMapping("/publish")
    ApiResponse<Integer> republish(CallerContext caller) {
        return ApiResponse.ok("published", service.republish(tenant(caller)));
    }
}
