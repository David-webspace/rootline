package dev.rootline.auth.iface.rest;

import static dev.rootline.auth.iface.rest.Callers.tenant;

import dev.rootline.auth.application.service.PermissionDictQueryService;
import dev.rootline.auth.application.shared.Views.PermissionDictEntryView;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 權限字典（給角色管理畫面挑選可授予的權限）。 */
@RestController
@RequestMapping("/api/permissions/dict")
class PermissionDictController {

    private final PermissionDictQueryService queries;

    PermissionDictController(PermissionDictQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @RequiresPermission(IamPermissions.ROLE_READ)
    ApiResponse<List<PermissionDictEntryView>> list(CallerContext caller) {
        return ApiResponse.ok(queries.list(tenant(caller)));
    }
}
