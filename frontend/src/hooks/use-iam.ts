"use client";

import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api";
import type { Group, PermissionContext, PermissionDictEntry, Role, User } from "@/lib/iam-types";

export const iamKeys = {
  users: ["iam", "users"] as const,
  roles: ["iam", "roles"] as const,
  groups: ["iam", "groups"] as const,
  dictionary: ["iam", "dictionary"] as const,
  context: (username: string) => ["iam", "context", username] as const,
};

export const useUsers = (enabled = true) =>
  useQuery({ queryKey: iamKeys.users, queryFn: () => api<User[]>("users"), enabled });

export const useRoles = (enabled = true) =>
  useQuery({ queryKey: iamKeys.roles, queryFn: () => api<Role[]>("roles"), enabled });

export const useGroups = (enabled = true) =>
  useQuery({ queryKey: iamKeys.groups, queryFn: () => api<Group[]>("groups"), enabled });

export const usePermissionDictionary = (enabled = true) =>
  useQuery({ queryKey: iamKeys.dictionary, queryFn: () => api<PermissionDictEntry[]>("permissions/dict"), enabled });

export const usePermissionContext = (username: string | null) =>
  useQuery({
    queryKey: iamKeys.context(username ?? ""),
    queryFn: () => api<PermissionContext>(`users/${encodeURIComponent(username!)}/permissions-context`),
    enabled: !!username,
  });

/**
 * 寫入操作的共用包裝：成功後提示並讓相關清單重新抓取；失敗時顯示後端回的錯誤訊息
 * （後端訊息已經說明原因，例如「不能停用自己」）。
 */
export function useIamMutation<TVariables>(
  request: (variables: TVariables) => Promise<unknown>,
  options: { success: string | ((variables: TVariables) => string); invalidate: QueryKey[] },
) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (_data, variables) => {
      toast.success(typeof options.success === "function" ? options.success(variables) : options.success);
      // 任何人的權限脈絡都可能受影響（角色、群組變動），一併失效
      [...options.invalidate, ["iam", "context"]].forEach((queryKey) => client.invalidateQueries({ queryKey }));
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : "操作失敗，請稍後再試");
    },
  });
}

export const path = (...segments: string[]) => segments.map(encodeURIComponent).join("/");
