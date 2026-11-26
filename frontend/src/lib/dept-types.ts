/** dept-tree-service 的回應型別（對應 DepartmentViews / DepartmentTemporalQueryService）。 */

export type DeptStatus = "ACTIVE" | "DISABLED";

export type TreeNode = {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  status: DeptStatus;
  sortOrder: number;
  /** 只有查詢帶 includeDeleted 才可能為 true。 */
  deleted: boolean;
  depth: number;
  directEmployeeCount: number;
  totalEmployeeCount: number;
  children: TreeNode[];
};

export type FlatNode = Omit<TreeNode, "children">;

export type RootsPage = { content: FlatNode[]; totalElements: number; page: number; size: number };

/** 某時間點重播出來的部門狀態。 */
export type DeptState = {
  asOf: string;
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  status: DeptStatus;
  sortOrder: number;
  deleted: boolean;
  employees: string[];
  version: number;
  lastModifiedAt: string;
  lastModifiedBy: string;
};

export type HistoryEntry = {
  version: number;
  eventType: string;
  /** 原樣保留後端字串（微秒精度），查詢時間點時直接回傳，不經過 Date 以免截斷成毫秒。 */
  occurredAt: string;
  operator: string;
  payload: Record<string, unknown>;
};

export type RestoreResult = { restored: number; reattachedToRoot: boolean };
export type MergeResult = { movedChildren: number; movedEmployees: number };

/** 部門代碼格式，與後端 DepartmentCode 相同。 */
export const DEPT_CODE_PATTERN = /^[A-Z0-9][A-Z0-9_-]{0,49}$/;
