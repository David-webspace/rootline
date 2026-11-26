package dev.rootline.auth.iface.rest;

/** auth-service 各 endpoint 需要的權限。 */
final class IamPermissions {

    static final String USER_READ = "IAM:USER_READ";
    static final String USER_WRITE = "IAM:USER_WRITE";
    static final String ROLE_READ = "IAM:ROLE_READ";
    static final String ROLE_WRITE = "IAM:ROLE_WRITE";
    static final String GROUP_READ = "IAM:GROUP_READ";
    static final String GROUP_WRITE = "IAM:GROUP_WRITE";

    private IamPermissions() {
    }
}
