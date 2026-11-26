import { NextResponse, type NextRequest } from "next/server";
import { decodeClaims } from "@/lib/server/claims";
import { GATEWAY_URL, SESSION_COOKIE } from "@/lib/server/config";
import { rejectCrossSite } from "@/lib/server/csrf";

/**
 * 登入：轉給 Gateway，成功後把 JWT 存進 httpOnly cookie，回給瀏覽器的只有使用者資訊、不含 token。
 */
export async function POST(request: NextRequest) {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;
  const body = await request.json().catch(() => null);
  const { tenantCode, username, password } = (body ?? {}) as Record<string, unknown>;
  if (typeof tenantCode !== "string" || typeof username !== "string" || typeof password !== "string") {
    return NextResponse.json({ code: "VALIDATION_FAILED", message: "請輸入租戶代碼、帳號與密碼", data: null }, { status: 400 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${GATEWAY_URL}/api/auth/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ tenantCode, username, password }),
      cache: "no-store",
    });
  } catch {
    return NextResponse.json({ code: "GATEWAY_UNREACHABLE", message: "無法連線到伺服器，請稍後再試", data: null }, { status: 502 });
  }

  const payload = await upstream.json().catch(() => null);
  if (!upstream.ok || !payload?.data?.token) {
    return NextResponse.json(payload ?? { code: "LOGIN_FAILED", message: "登入失敗", data: null }, {
      status: upstream.status === 200 ? 502 : upstream.status,
      headers: retryAfter(upstream),
    });
  }

  const claims = decodeClaims(payload.data.token);
  if (!claims) {
    return NextResponse.json({ code: "INVALID_TOKEN", message: "伺服器回傳的登入憑證無效", data: null }, { status: 502 });
  }

  const response = NextResponse.json({
    code: "OK",
    message: "success",
    data: { username: claims.username, tenantId: claims.tenantId, roles: claims.roles },
  });
  response.cookies.set(SESSION_COOKIE, payload.data.token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    expires: new Date(claims.expiresAt),
  });
  return response;
}

function retryAfter(upstream: Response): HeadersInit | undefined {
  const value = upstream.headers.get("retry-after");
  return value ? { "retry-after": value } : undefined;
}
