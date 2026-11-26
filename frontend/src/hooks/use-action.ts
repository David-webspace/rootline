"use client";

import { useMutation, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { toast } from "sonner";
import { ApiError } from "@/lib/api";

/** 後端錯誤訊息是英文；呼叫端提供常見錯誤碼的中文說明，沒列到的沿用後端訊息。 */
export function describeError(error: unknown, messages: Record<string, string> = {}): string {
  if (!(error instanceof ApiError)) return "操作失敗，請稍後再試";
  return messages[error.code] ?? error.message;
}

/**
 * 租戶、權限定義、API 規則、實名認證共用的寫入包裝：成功提示並讓相關查詢重抓，失敗顯示中文錯誤。
 * 這幾個服務寫完立即可讀（不經 Kafka 投影），所以不需要 optimistic update。
 */
export function useAction<TVariables, TResult = unknown>(
  request: (variables: TVariables) => Promise<TResult>,
  options: {
    success: string | ((variables: TVariables, result: TResult) => string);
    invalidate: QueryKey[];
    errors?: Record<string, string>;
  },
) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (result, variables) => {
      toast.success(typeof options.success === "function" ? options.success(variables, result) : options.success);
      options.invalidate.forEach((queryKey) => client.invalidateQueries({ queryKey }));
    },
    onError: (error) => toast.error(describeError(error, options.errors)),
  });
}
