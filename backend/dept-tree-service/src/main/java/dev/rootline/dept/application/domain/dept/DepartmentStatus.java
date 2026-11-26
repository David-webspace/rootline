package dev.rootline.dept.application.domain.dept;

public enum DepartmentStatus {
    ACTIVE,
    /** 停用：不能改名、搬移、指派人員，也不能當新部門的上層。被合併掉的部門也是此狀態。 */
    DISABLED
}
