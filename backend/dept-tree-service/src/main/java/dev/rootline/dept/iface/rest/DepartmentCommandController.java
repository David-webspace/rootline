package dev.rootline.dept.iface.rest;

import static dev.rootline.dept.iface.rest.Callers.tenant;

import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.service.DepartmentCommandService;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 組織樹的寫入 API。tenant 一律取自呼叫者身分（Gateway 注入），不接受從路徑或 body 指定。 */
@RestController
@RequestMapping("/api/departments")
class DepartmentCommandController {

    /** 單次批次建樹的節點上限，避免一個請求建出巨量節點佔住租戶鎖。 */
    static final int MAX_TREE_NODES = 500;

    private final DepartmentCommandService commands;

    DepartmentCommandController(DepartmentCommandService commands) {
        this.commands = commands;
    }

    @PostMapping
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ResponseEntity<ApiResponse<String>> create(CallerContext caller, @Valid @RequestBody Requests.CreateDepartment request) {
        String id = commands.create(tenant(caller), request.id(), request.parentId(), request.code(), request.name(),
                caller.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created", id));
    }

    @PostMapping("/tree")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ResponseEntity<ApiResponse<List<String>>> createTree(CallerContext caller,
                                                         @Valid @RequestBody Requests.CreateTree request) {
        if (request.root().size() > MAX_TREE_NODES) {
            throw DomainException.invalid("TREE_TOO_LARGE", "A single tree request may create at most "
                    + MAX_TREE_NODES + " departments");
        }
        List<String> ids = commands.createTree(tenant(caller), request.parentId(), request.root().toSpec(),
                caller.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("created", ids));
    }

    @PostMapping("/move")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ApiResponse<Void> move(CallerContext caller, @Valid @RequestBody Requests.Move request) {
        commands.move(tenant(caller), request.departmentId(), request.newParentId(), caller.userId());
        return ApiResponse.ok("moved", null);
    }

    @PatchMapping("/{id}/name")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ApiResponse<Void> rename(CallerContext caller, @PathVariable String id, @Valid @RequestBody Requests.Rename request) {
        commands.rename(tenant(caller), id, request.name(), caller.userId());
        return ApiResponse.ok("renamed", null);
    }

    @PatchMapping("/{id}/sort-order")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ApiResponse<Void> sortOrder(CallerContext caller, @PathVariable String id,
                                @Valid @RequestBody Requests.SortOrder request) {
        commands.changeSortOrder(tenant(caller), id, request.sortOrder(), caller.userId());
        return ApiResponse.ok("sort order changed", null);
    }

    /** 連同子樹一起停用，回傳實際被停用的部門數。 */
    @PatchMapping("/{id}/disable")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ApiResponse<Integer> disable(CallerContext caller, @PathVariable String id) {
        return ApiResponse.ok("disabled", commands.disable(tenant(caller), id, caller.userId()));
    }

    /** 連同子樹邏輯刪除，回傳刪除的部門數。可用 restore 復原。 */
    @DeleteMapping("/{id}")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ApiResponse<Integer> delete(CallerContext caller, @PathVariable String id) {
        return ApiResponse.ok("deleted", commands.delete(tenant(caller), id, caller.userId()));
    }

    @PostMapping("/{id}/restore")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ApiResponse<DepartmentCommandService.RestoreResult> restore(CallerContext caller, @PathVariable String id) {
        return ApiResponse.ok("restored", commands.restore(tenant(caller), id, caller.userId()));
    }

    @PostMapping("/{id}/merge")
    @RequiresPermission(DeptPermissions.TREE_WRITE)
    ApiResponse<DepartmentCommandService.MergeResult> merge(CallerContext caller, @PathVariable String id,
                                                            @Valid @RequestBody Requests.Merge request) {
        return ApiResponse.ok("merged", commands.merge(tenant(caller), id, request.targetDeptId(), caller.userId()));
    }

    @PostMapping("/{id}/employees")
    @RequiresPermission(DeptPermissions.STAFF_WRITE)
    ApiResponse<Void> assign(CallerContext caller, @PathVariable String id,
                             @Valid @RequestBody Requests.AssignEmployee request) {
        commands.assignEmployee(tenant(caller), id, request.employeeId(), caller.userId());
        return ApiResponse.ok("assigned", null);
    }

    @DeleteMapping("/{id}/employees/{employeeId}")
    @RequiresPermission(DeptPermissions.STAFF_WRITE)
    ApiResponse<Void> unassign(CallerContext caller, @PathVariable String id, @PathVariable String employeeId) {
        commands.unassignEmployee(tenant(caller), id, employeeId, caller.userId());
        return ApiResponse.ok("unassigned", null);
    }
}
