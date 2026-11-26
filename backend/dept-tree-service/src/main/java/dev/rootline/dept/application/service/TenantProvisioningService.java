package dev.rootline.dept.application.service;

import dev.rootline.dept.application.domain.dept.DepartmentId;
import dev.rootline.dept.application.domain.shared.TenantId;
import dev.rootline.dept.application.port.DepartmentReadModel;
import dev.rootline.dept.application.port.PermissionDefinitionRepository;
import dev.rootline.dept.application.port.ProcessedEvents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租戶開通時初始化組織樹這一側：
 * <ol>
 *   <li>建立根部門（名稱為公司名稱）</li>
 *   <li>定義平台內建權限。權限定義事件會再經 Kafka 流到 auth 的權限字典，
 *       auth 才能把這些權限授予角色</li>
 * </ol>
 * 每一步都先檢查是否已存在，重送的事件不會建出第二個根部門。
 */
@Service
public class TenantProvisioningService {

    public static final String CONSUMER = "dept-tenant-provisioner";
    public static final String ROOT_ID = "root";
    static final String SYSTEM_OPERATOR = "system";

    /** 平台內建的權限：{系統, 代碼, 名稱}。 */
    static final List<String[]> BUILT_IN_PERMISSIONS = List.of(
            new String[]{"IAM", "USER_READ", "檢視使用者"},
            new String[]{"IAM", "USER_WRITE", "管理使用者"},
            new String[]{"IAM", "ROLE_READ", "檢視角色與權限字典"},
            new String[]{"IAM", "ROLE_WRITE", "管理角色"},
            new String[]{"IAM", "GROUP_READ", "檢視群組"},
            new String[]{"IAM", "GROUP_WRITE", "管理群組"},
            new String[]{"DEPT", "TREE_READ", "檢視組織樹"},
            new String[]{"DEPT", "TREE_WRITE", "編輯組織樹"},
            new String[]{"DEPT", "STAFF_WRITE", "調整部門人員"},
            new String[]{"DEPT", "PROJECTION_REBUILD", "重建組織樹投影"},
            new String[]{"DEPT", "PERMISSION_WRITE", "管理權限定義"},
            new String[]{"DEPT", "API_RULE_WRITE", "管理動態 API 規則"},
            new String[]{"KYC", "REVIEW", "審核實名資料"});

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

    private final ProcessedEvents processed;
    private final DepartmentReadModel readModel;
    private final DepartmentCommandService departments;
    private final PermissionDefinitionRepository definitions;
    private final PermissionDefinitionService permissions;

    public TenantProvisioningService(ProcessedEvents processed, DepartmentReadModel readModel,
                                     DepartmentCommandService departments,
                                     PermissionDefinitionRepository definitions,
                                     PermissionDefinitionService permissions) {
        this.processed = processed;
        this.readModel = readModel;
        this.departments = departments;
        this.definitions = definitions;
        this.permissions = permissions;
    }

    /** @return 是否有實際處理（重複事件或非開通事件回傳 false） */
    @Transactional
    public boolean apply(TenantEvent event) {
        if (!processed.markProcessed(event.eventId(), CONSUMER) || !"TenantProvisioned".equals(event.eventType())) {
            return false;
        }
        TenantId tenant = TenantId.of(event.tenantId());
        if (readModel.find(tenant, DepartmentId.of(ROOT_ID)).isEmpty()) {
            Object companyName = event.payload().get("companyName");
            if (!(companyName instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("TenantProvisioned payload is missing 'companyName'");
            }
            departments.create(tenant, ROOT_ID, null, "ROOT", name, SYSTEM_OPERATOR);
        }
        int defined = 0;
        for (String[] p : BUILT_IN_PERMISSIONS) {
            if (definitions.find(tenant, p[0], p[1]).isEmpty()) {
                permissions.define(tenant, p[0], p[1], p[2], "", SYSTEM_OPERATOR);
                defined++;
            }
        }
        log.info("Initialised org tree for tenant {} ({} permission definitions)", tenant, defined);
        return true;
    }

    public record TenantEvent(UUID eventId, String eventType, String tenantId, Map<String, Object> payload) {
    }
}
