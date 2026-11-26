package dev.rootline.tenant.application.domain.tenant;

/** 訂閱方案。等級只能往上升，降級需走另外的商務流程（本專案不實作）。 */
public enum PlanType {
    FREE(1),
    PRO(2),
    ENTERPRISE(3);

    private final int level;

    PlanType(int level) {
        this.level = level;
    }

    public boolean isUpgradeFrom(PlanType current) {
        return level > current.level;
    }
}
