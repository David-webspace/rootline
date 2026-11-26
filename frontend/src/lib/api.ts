/** 後端統一的回應格式。 */
export type ApiEnvelope<T> = { code: string; message: string; data: T };

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

/**
 * 呼叫 BFF。所有請求都帶 CSRF header（BFF 對寫入請求強制檢查）。
 * 401（session 失效）會以 ApiError 丟出，由 Providers 的全域錯誤處理導回登入頁。
 */
export async function api<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(path.startsWith("/api/") ? path : `/api/bff/${path.replace(/^\//, "")}`, {
    method: init.method ?? "GET",
    headers: {
      "x-rootline-csrf": "1",
      ...(init.body === undefined ? {} : { "content-type": "application/json" }),
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
    cache: "no-store",
  });

  const envelope = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!response.ok || !envelope) {
    throw new ApiError(response.status, envelope?.code ?? "UNKNOWN", envelope?.message ?? describe(response.status));
  }
  return envelope.data;
}

function describe(status: number): string {
  if (status === 429) return "請求太頻繁，請稍後再試";
  if (status >= 500) return "伺服器暫時無法處理，請稍後再試";
  return "請求失敗";
}
