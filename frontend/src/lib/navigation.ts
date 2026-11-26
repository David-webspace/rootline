/** 側欄項目。permissions 為「任一即可看到」；空陣列代表所有登入者都看得到。 */
export type NavItem = {
  href: string;
  label: string;
  icon: "tree" | "shield" | "building" | "key" | "badge";
  permissions: string[];
  /** 尚未完成的功能：顯示但不可點，標示預計的里程碑。 */
  comingIn?: string;
  /** 只在特定租戶顯示（平台功能只給 platform 租戶，一般租戶的 *:ADMIN_ALL 不算）。 */
  onlyTenant?: string;
};

export const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard/org-tree", label: "組織樹", icon: "tree", permissions: ["DEPT:TREE_READ"] },
  {
    href: "/dashboard/iam",
    label: "身分與權限",
    icon: "shield",
    permissions: ["IAM:USER_READ", "IAM:ROLE_READ", "IAM:GROUP_READ"],
  },
  {
    href: "/dashboard/tenants",
    label: "租戶管理",
    icon: "building",
    permissions: ["PLATFORM:TENANT_READ"],
    onlyTenant: "platform",
  },
  {
    href: "/dashboard/dept-permissions",
    label: "權限定義與規則",
    icon: "key",
    permissions: ["DEPT:PERMISSION_WRITE", "DEPT:API_RULE_WRITE"],
  },
  { href: "/dashboard/kyc", label: "實名認證", icon: "badge", permissions: [] },
];
