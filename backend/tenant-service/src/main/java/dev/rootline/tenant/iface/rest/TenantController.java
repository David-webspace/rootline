package dev.rootline.tenant.iface.rest;

import dev.rootline.tenant.application.domain.shared.DomainException;
import dev.rootline.tenant.application.domain.tenant.Tenant;
import dev.rootline.tenant.application.port.TenantPorts.Page;
import dev.rootline.tenant.application.service.TenantService;
import dev.rootline.tenant.application.service.TenantService.View;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台層級的租戶管理。只有 {@code platform} 租戶裡、具備對應權限的營運人員能用；
 * 一般租戶的管理員即使有 {@code *:ADMIN_ALL}（那只是「自己租戶內的全部權限」）也不行。
 */
@RestController
@RequestMapping("/api/platform/tenants")
class TenantController {

    static final String TENANT_READ = "PLATFORM:TENANT_READ";
    static final String TENANT_WRITE = "PLATFORM:TENANT_WRITE";

    private final TenantService tenants;

    TenantController(TenantService tenants) {
        this.tenants = tenants;
    }

    record Provision(@NotBlank String tenantId, @NotBlank String companyName, @NotBlank String plan,
                     @NotBlank String adminUsername, @NotBlank String adminEmail,
                     @NotBlank @Size(max = 200) String adminPassword) {

        @Override
        public String toString() {
            return "Provision[tenantId=" + tenantId + ", admin=" + adminUsername + "]";
        }
    }

    record Suspend(@NotBlank String reason) {
    }

    record ChangePlan(@NotBlank String plan) {
    }

    @PostMapping
    @RequiresPermission(TENANT_WRITE)
    ResponseEntity<ApiResponse<View>> provision(CallerContext caller, @Valid @RequestBody Provision request) {
        requirePlatform(caller);
        View view = tenants.provision(request.tenantId(), request.companyName(), request.plan(), request.adminUsername(),
                request.adminEmail(), request.adminPassword(), caller.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("provisioned", view));
    }

    @PostMapping("/{tenantId}/suspend")
    @RequiresPermission(TENANT_WRITE)
    ApiResponse<View> suspend(CallerContext caller, @PathVariable String tenantId, @Valid @RequestBody Suspend request) {
        requirePlatform(caller);
        return ApiResponse.ok("suspended", tenants.suspend(tenantId, request.reason(), caller.userId()));
    }

    @PostMapping("/{tenantId}/reactivate")
    @RequiresPermission(TENANT_WRITE)
    ApiResponse<View> reactivate(CallerContext caller, @PathVariable String tenantId) {
        requirePlatform(caller);
        return ApiResponse.ok("reactivated", tenants.reactivate(tenantId, caller.userId()));
    }

    @PutMapping("/{tenantId}/plan")
    @RequiresPermission(TENANT_WRITE)
    ApiResponse<View> upgrade(CallerContext caller, @PathVariable String tenantId,
                              @Valid @RequestBody ChangePlan request) {
        requirePlatform(caller);
        return ApiResponse.ok("upgraded", tenants.upgradePlan(tenantId, request.plan(), caller.userId()));
    }

    @GetMapping("/{tenantId}")
    @RequiresPermission(TENANT_READ)
    ApiResponse<View> get(CallerContext caller, @PathVariable String tenantId) {
        requirePlatform(caller);
        return ApiResponse.ok(tenants.get(tenantId));
    }

    @GetMapping
    @RequiresPermission(TENANT_READ)
    ApiResponse<Page<View>> search(CallerContext caller, @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        requirePlatform(caller);
        return ApiResponse.ok(tenants.search(keyword, status, page, size));
    }

    private static void requirePlatform(CallerContext caller) {
        if (!Tenant.PLATFORM.equals(caller.tenantId())) {
            throw new DomainException(DomainException.Kind.RULE_VIOLATION, "PLATFORM_ONLY",
                    "Tenant management is restricted to platform operators");
        }
    }
}
