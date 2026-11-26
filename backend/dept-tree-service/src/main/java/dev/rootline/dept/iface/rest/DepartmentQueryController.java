package dev.rootline.dept.iface.rest;

import static dev.rootline.dept.iface.rest.Callers.tenant;

import dev.rootline.dept.application.service.DepartmentQueryService;
import dev.rootline.dept.application.service.DepartmentViews.FlatNode;
import dev.rootline.dept.application.service.DepartmentViews.Hierarchy;
import dev.rootline.dept.application.service.DepartmentViews.RootsPage;
import dev.rootline.dept.application.service.DepartmentViews.TreeNode;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
class DepartmentQueryController {

    private final DepartmentQueryService queries;

    DepartmentQueryController(DepartmentQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/roots")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<RootsPage> roots(CallerContext caller, @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String name) {
        return ApiResponse.ok(queries.roots(tenant(caller), name, page, size));
    }

    @GetMapping("/search")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<List<FlatNode>> search(CallerContext caller, @RequestParam String keyword) {
        return ApiResponse.ok(queries.search(tenant(caller), keyword));
    }

    @GetMapping("/{id}/tree")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<TreeNode> tree(CallerContext caller, @PathVariable String id,
                               @RequestParam(defaultValue = "false") boolean includeDisabled,
                               @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return ApiResponse.ok(queries.tree(tenant(caller), id, includeDisabled, includeDeleted));
    }

    @GetMapping("/{id}/breadcrumbs")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<List<FlatNode>> breadcrumbs(CallerContext caller, @PathVariable String id) {
        return ApiResponse.ok(queries.breadcrumbs(tenant(caller), id));
    }

    @GetMapping("/{id}/hierarchy")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<Hierarchy> hierarchy(CallerContext caller, @PathVariable String id) {
        return ApiResponse.ok(queries.hierarchy(tenant(caller), id));
    }

    /** 本人可查自己所屬的部門樹。 */
    @GetMapping("/employees/{employeeId}/trees")
    @RequiresPermission(value = DeptPermissions.TREE_READ, selfPathVariable = "employeeId")
    ApiResponse<List<TreeNode>> employeeTrees(CallerContext caller, @PathVariable String employeeId) {
        return ApiResponse.ok(queries.employeeTrees(tenant(caller), employeeId));
    }
}
