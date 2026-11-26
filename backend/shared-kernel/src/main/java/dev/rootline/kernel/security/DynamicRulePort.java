package dev.rootline.kernel.security;

import java.util.List;

/**
 * 第二軌（動態）權限規則的輸出埠：依租戶、HTTP method 與路徑，查出「額外」需要的權限。
 * <p>
 * 動態規則只能收緊、不能放寬：它與 {@link RequiresPermission} 是 AND 關係，
 * 租戶可以對某些 API 追加要求（例如匯出功能要再多一個權限），但無法讓沒權限的人通過。
 */
@FunctionalInterface
public interface DynamicRulePort {

    /** @return 必須全部具備的權限；沒有規則時回傳空 list */
    List<String> requiredPermissions(String tenantId, String httpMethod, String path);
}
