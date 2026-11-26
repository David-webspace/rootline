package dev.rootline.dept.iface.rest;

import static dev.rootline.dept.iface.rest.Callers.tenant;

import dev.rootline.dept.application.service.DepartmentViews.RebuildResult;
import dev.rootline.dept.application.service.ProjectionRebuildService;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 從事件庫重建呼叫者所屬租戶的投影。 */
@RestController
@RequestMapping("/api/departments/projections")
class ProjectionAdminController {

    private final ProjectionRebuildService rebuild;

    ProjectionAdminController(ProjectionRebuildService rebuild) {
        this.rebuild = rebuild;
    }

    @PostMapping("/rebuild")
    @RequiresPermission(DeptPermissions.PROJECTION_REBUILD)
    ApiResponse<RebuildResult> rebuild(CallerContext caller) {
        return ApiResponse.ok("rebuilt", rebuild.rebuild(tenant(caller)));
    }
}
