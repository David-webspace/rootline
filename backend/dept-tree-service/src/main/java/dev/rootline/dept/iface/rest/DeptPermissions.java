package dev.rootline.dept.iface.rest;

/** dept-tree-service 各 endpoint 需要的權限。 */
final class DeptPermissions {

    static final String TREE_READ = "DEPT:TREE_READ";
    static final String TREE_WRITE = "DEPT:TREE_WRITE";
    static final String STAFF_WRITE = "DEPT:STAFF_WRITE";
    static final String PROJECTION_REBUILD = "DEPT:PROJECTION_REBUILD";
    static final String PERMISSION_WRITE = "DEPT:PERMISSION_WRITE";
    static final String API_RULE_WRITE = "DEPT:API_RULE_WRITE";

    private DeptPermissions() {
    }
}
