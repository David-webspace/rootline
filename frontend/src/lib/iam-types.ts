/** 對應 auth-service 的回應格式。 */
export type UserStatus = "ACTIVE" | "LOCKED" | "DEACTIVATED";

export type User = { id: string; username: string; email: string; status: UserStatus; roles: string[] };

export type Permission = { systemCode: string; permissionCode: string; permissionName: string };

export type Role = { id: string; roleCode: string; roleName: string; systemRoot: boolean; permissions: Permission[] };

export type Group = { groupCode: string; groupName: string; members: string[]; roles: string[] };

export type PermissionDictEntry = Permission & { description: string; version: number };

export type PermissionContext = {
  username: string;
  email: string;
  status: UserStatus;
  personalRoles: string[];
  groupRoles: { groupCode: string; groupName: string; roleCodes: string[] }[];
  permissions: Permission[];
  departments: { id: string; name: string }[];
};

export const authority = (p: Pick<Permission, "systemCode" | "permissionCode">) => `${p.systemCode}:${p.permissionCode}`;
