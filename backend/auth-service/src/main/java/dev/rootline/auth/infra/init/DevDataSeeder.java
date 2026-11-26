package dev.rootline.auth.infra.init;

import dev.rootline.auth.application.domain.permission.PermissionDefinition;
import dev.rootline.auth.application.domain.role.PermissionKey;
import dev.rootline.auth.application.domain.role.RoleCode;
import dev.rootline.auth.application.domain.shared.TenantId;
import dev.rootline.auth.application.domain.user.Username;
import dev.rootline.auth.application.port.PermissionDictionary;
import dev.rootline.auth.application.port.RoleRepository;
import dev.rootline.auth.application.port.UserRepository;
import dev.rootline.auth.application.service.RoleCommandService;
import dev.rootline.auth.application.service.UserCommandService;
import dev.rootline.auth.infra.persistence.TenantRegistryEntity;
import dev.rootline.auth.infra.persistence.TenantRegistryJpaRepository;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本機開發用的展示資料（虛構租戶 northwind）。只在 {@code dev} profile 執行，可重複執行。
 * <p>
 * M5 之前，tenant-service 與 dept-tree-service 還沒有發事件過來，
 * 所以這裡先手動補上「租戶已開通」與「權限字典」兩份投影。
 * 權限字典以版本 0 寫入：dept-tree 發來的正式定義（版本 ≥ 1）一律覆蓋它。
 *
 * 管理員密碼讀環境變數 {@code ROOTLINE_SEED_ADMIN_PASSWORD}，沒設就不建管理員。
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final TenantId TENANT = TenantId.of("northwind");

    private static final List<String[]> IAM_PERMISSIONS = List.of(
            new String[]{"USER_READ", "檢視使用者"},
            new String[]{"USER_WRITE", "管理使用者"},
            new String[]{"ROLE_READ", "檢視角色與權限字典"},
            new String[]{"ROLE_WRITE", "管理角色"},
            new String[]{"GROUP_READ", "檢視群組"},
            new String[]{"GROUP_WRITE", "管理群組"});

    /** M4 起改由 dept-tree-service 發佈權限定義事件，這裡只是先讓 dev 環境可以授權。 */
    private static final List<String[]> DEPT_PERMISSIONS = List.of(
            new String[]{"TREE_READ", "檢視組織樹"},
            new String[]{"TREE_WRITE", "編輯組織樹"},
            new String[]{"STAFF_WRITE", "調整部門人員"},
            new String[]{"PROJECTION_REBUILD", "重建組織樹投影"});

    private final TenantRegistryJpaRepository tenants;
    private final PermissionDictionary dictionary;
    private final RoleRepository roles;
    private final UserRepository users;
    private final RoleCommandService roleCommands;
    private final UserCommandService userCommands;
    private final String adminPassword;

    DevDataSeeder(TenantRegistryJpaRepository tenants, PermissionDictionary dictionary, RoleRepository roles,
                  UserRepository users, RoleCommandService roleCommands, UserCommandService userCommands,
                  @Value("${ROOTLINE_SEED_ADMIN_PASSWORD:}") String adminPassword) {
        this.tenants = tenants;
        this.dictionary = dictionary;
        this.roles = roles;
        this.users = users;
        this.roleCommands = roleCommands;
        this.userCommands = userCommands;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!tenants.existsById(TENANT.value())) {
            tenants.save(new TenantRegistryEntity(TENANT.value(), TenantRegistryEntity.ACTIVE));
        }

        IAM_PERMISSIONS.forEach(p -> dictionary.upsertIfNewer(
                new PermissionDefinition(TENANT, new PermissionKey("IAM", p[0]), p[1], "", 0)));
        DEPT_PERMISSIONS.forEach(p -> dictionary.upsertIfNewer(
                new PermissionDefinition(TENANT, new PermissionKey("DEPT", p[0]), p[1], "", 0)));

        if (!roles.exists(TENANT, RoleCode.of("TENANT_ADMIN"))) {
            roleCommands.createSystemRoot(TENANT, "TENANT_ADMIN", "租戶管理員", Set.of(PermissionKey.SUPER_ADMIN));
        }
        if (!roles.exists(TENANT, RoleCode.of("HR_VIEWER"))) {
            roleCommands.create(TENANT, "HR_VIEWER", "人資檢視");
            roleCommands.grantPermission(TENANT, "HR_VIEWER", "IAM", "USER_READ");
            roleCommands.grantPermission(TENANT, "HR_VIEWER", "IAM", "GROUP_READ");
        }
        if (roles.findByCode(TENANT, RoleCode.of("HR_VIEWER"))
                .filter(r -> !r.permissions().contains(new PermissionKey("DEPT", "TREE_READ"))).isPresent()) {
            roleCommands.grantPermission(TENANT, "HR_VIEWER", "DEPT", "TREE_READ");
        }

        if (adminPassword.isBlank()) {
            log.warn("ROOTLINE_SEED_ADMIN_PASSWORD is not set; skipping admin account for tenant {}", TENANT);
        } else if (!users.exists(TENANT, Username.of("admin"))) {
            userCommands.create(TENANT, "admin", "admin@northwind.example", adminPassword);
            userCommands.assignRole(TENANT, "admin", "TENANT_ADMIN");
            log.info("Seeded admin account for tenant {}", TENANT);
        }
        seedPlatform();
    }

    /**
     * 平台營運租戶：operator 帳號可透過 tenant-service 開通 / 停權其他租戶。
     * 一般租戶的 TENANT_ADMIN 只有「自己租戶內」的 *:ADMIN_ALL，碰不到平台功能。
     */
    private void seedPlatform() {
        TenantId platform = TenantId.of("platform");
        if (!tenants.existsById(platform.value())) {
            tenants.save(new TenantRegistryEntity(platform.value(), TenantRegistryEntity.ACTIVE));
        }
        dictionary.upsertIfNewer(new PermissionDefinition(platform, new PermissionKey("PLATFORM", "TENANT_READ"),
                "檢視租戶", "", 0));
        dictionary.upsertIfNewer(new PermissionDefinition(platform, new PermissionKey("PLATFORM", "TENANT_WRITE"),
                "開通與停權租戶", "", 0));
        if (!roles.exists(platform, RoleCode.of("PLATFORM_OPERATOR"))) {
            roleCommands.createSystemRoot(platform, "PLATFORM_OPERATOR", "平台營運", Set.of(
                    new PermissionKey("PLATFORM", "TENANT_READ"), new PermissionKey("PLATFORM", "TENANT_WRITE")));
        }
        if (!adminPassword.isBlank() && !users.exists(platform, Username.of("operator"))) {
            userCommands.create(platform, "operator", "operator@rootline.example", adminPassword);
            userCommands.assignRole(platform, "operator", "PLATFORM_OPERATOR");
            log.info("Seeded platform operator account");
        }
    }
}
