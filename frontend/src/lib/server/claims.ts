import "server-only";

/** JWT 裡前端畫面需要的資訊。 */
export type SessionClaims = {
  username: string;
  tenantId: string;
  roles: string[];
  permissions: string[];
  expiresAt: number;
};

/**
 * 解析 JWT payload（不驗簽）。
 *
 * 這裡只用來決定「畫面要顯示什麼」與 proxy 的第一道導向；真正的授權每次都由 Gateway
 * 用 JWKS 驗簽、下游服務檢查權限。就算有人竄改 cookie，最多看到按鈕，按下去仍會被後端擋下。
 */
export function decodeClaims(token: string | undefined, now = Date.now()): SessionClaims | null {
  if (!token) return null;
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
    const expiresAt = typeof payload.exp === "number" ? payload.exp * 1000 : 0;
    if (!payload.sub || !payload.tid || expiresAt <= now) return null;
    return {
      username: String(payload.sub),
      tenantId: String(payload.tid),
      roles: Array.isArray(payload.roles) ? payload.roles.map(String) : [],
      permissions: Array.isArray(payload.perms) ? payload.perms.map(String) : [],
      expiresAt,
    };
  } catch {
    return null;
  }
}
