package dev.rootline.dept.iface.rest;

import static dev.rootline.dept.iface.rest.Callers.tenant;

import dev.rootline.dept.application.domain.shared.DomainException;
import dev.rootline.dept.application.service.DepartmentTemporalQueryService;
import dev.rootline.dept.application.service.DepartmentTemporalQueryService.HistoryEntry;
import dev.rootline.dept.application.service.DepartmentTemporalQueryService.State;
import dev.rootline.kernel.security.CallerContext;
import dev.rootline.kernel.security.RequiresPermission;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 時光機：事件時間軸與任一時間點的狀態。已刪除的部門也查得到。 */
@RestController
@RequestMapping("/api/departments/{id}")
class DepartmentTemporalController {

    private final DepartmentTemporalQueryService temporal;

    DepartmentTemporalController(DepartmentTemporalQueryService temporal) {
        this.temporal = temporal;
    }

    @GetMapping("/events")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<List<HistoryEntry>> events(CallerContext caller, @PathVariable String id) {
        return ApiResponse.ok(temporal.history(tenant(caller), id));
    }

    /** @param timestamp ISO-8601，例如 {@code 2026-11-25T08:00:00Z} */
    @GetMapping("/state/at")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<State> stateAt(CallerContext caller, @PathVariable String id, @RequestParam String timestamp) {
        Instant instant;
        try {
            instant = Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw DomainException.invalid("TIMESTAMP_INVALID", "timestamp must be ISO-8601, e.g. 2026-11-25T08:00:00Z");
        }
        return ApiResponse.ok(temporal.stateAt(tenant(caller), id, instant));
    }

    @GetMapping("/state/current")
    @RequiresPermission(DeptPermissions.TREE_READ)
    ApiResponse<State> current(CallerContext caller, @PathVariable String id) {
        return ApiResponse.ok(temporal.current(tenant(caller), id));
    }
}
