"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import type { DeptState, HistoryEntry, RootsPage, TreeNode } from "@/lib/dept-types";
import { path } from "./use-iam";

/** 後端錯誤訊息是英文，常見的業務規則在這裡轉成中文；沒列到的沿用後端訊息。 */
const DEPT_ERRORS: Record<string, string> = {
  DEPARTMENT_CODE_TAKEN: "部門代碼已被使用",
  DEPARTMENT_CODE_INVALID: "部門代碼只能用大寫英數、- 與 _，最多 50 字",
  DEPARTMENT_NAME_INVALID: "部門名稱不可空白",
  EMPLOYEE_ALREADY_ASSIGNED: "這位成員已在此部門",
  EMPLOYEE_NOT_ASSIGNED: "這位成員不在此部門",
  CYCLE_DETECTED: "不能搬到自己或自己的子部門底下",
  DEPARTMENT_ALREADY_DELETED: "部門已經被刪除",
  DEPARTMENT_DELETED: "部門已被刪除，請先復原",
  DEPARTMENT_DISABLED: "部門已停用，無法執行這個操作",
  DEPARTMENT_HAS_EMPLOYEES: "部門（或子部門）還有成員，請先移出",
  DEPARTMENT_NOT_DELETED: "部門沒有被刪除，不需要復原",
  MERGE_INTO_SELF: "不能合併到自己",
  MERGE_INTO_DESCENDANT: "不能合併到自己的子部門",
  PARENT_DISABLED: "目標上層部門已停用",
  PARENT_NOT_FOUND: "找不到目標上層部門",
  ROOT_NOT_DELETABLE: "最上層部門不能刪除",
  DEPARTMENT_NOT_FOUND: "找不到這個部門，可能已被其他人變更",
  DEPARTMENT_NOT_YET_EXISTED: "這個時間點部門還不存在",
};

export function describeDeptError(error: unknown): string {
  if (!(error instanceof ApiError)) return "操作失敗，請稍後再試";
  return DEPT_ERRORS[error.code] ?? error.message;
}

export const deptKeys = {
  all: ["dept"] as const,
  roots: ["dept", "roots"] as const,
  tree: (rootId: string, includeDeleted: boolean) => ["dept", "tree", rootId, includeDeleted] as const,
  state: (id: string) => ["dept", "state", id] as const,
  events: (id: string) => ["dept", "events", id] as const,
  stateAt: (id: string, at: string) => ["dept", "state-at", id, at] as const,
};

export const useRoots = () =>
  useQuery({ queryKey: deptKeys.roots, queryFn: () => api<RootsPage>("departments/roots?size=100") });

/** 停用的部門一律帶回來（畫面上以樣式區分）；已刪除的由開關決定，給時光機復活用。 */
export const useTree = (rootId: string | null, includeDeleted: boolean) =>
  useQuery({
    queryKey: deptKeys.tree(rootId ?? "", includeDeleted),
    queryFn: () =>
      api<TreeNode>(`departments/${path(rootId!)}/tree?includeDisabled=true&includeDeleted=${includeDeleted}`),
    enabled: !!rootId,
    // 切換「顯示已刪除」時保留舊樹，避免整棵樹閃成骨架
    placeholderData: keepPreviousData,
  });

export const useDeptState = (id: string | null) =>
  useQuery({
    queryKey: deptKeys.state(id ?? ""),
    queryFn: () => api<DeptState>(`departments/${path(id!)}/state/current`),
    enabled: !!id,
  });

export const useDeptEvents = (id: string | null) =>
  useQuery({
    queryKey: deptKeys.events(id ?? ""),
    queryFn: () => api<HistoryEntry[]>(`departments/${path(id!)}/events`),
    enabled: !!id,
  });

export const useDeptStateAt = (id: string | null, at: string | null) =>
  useQuery({
    queryKey: deptKeys.stateAt(id ?? "", at ?? ""),
    queryFn: () => api<DeptState>(`departments/${path(id!)}/state/at?timestamp=${encodeURIComponent(at!)}`),
    enabled: !!id && !!at,
    // 歷史狀態不會再變
    staleTime: Infinity,
    retry: false,
  });

/**
 * 組織樹的寫入。dept-tree 的投影與事件在同一個 transaction 更新，寫完立刻讀就是最新，
 * 所以成功後直接讓整個 dept 快取失效即可，不需要延遲或 optimistic update。
 */
export function useDeptMutation<TVariables, TResult = unknown>(
  request: (variables: TVariables) => Promise<TResult>,
  success: string | ((variables: TVariables, result: TResult) => string),
) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (result, variables) => {
      toast.success(typeof success === "function" ? success(variables, result) : success);
      client.invalidateQueries({ queryKey: deptKeys.all });
    },
    onError: (error) => {
      toast.error(describeDeptError(error));
      // 失敗也重抓：可能是別人剛改過，畫面要跟上
      client.invalidateQueries({ queryKey: deptKeys.all });
    },
  });
}
