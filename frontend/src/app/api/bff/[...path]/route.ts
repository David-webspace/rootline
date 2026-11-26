import { NextResponse, type NextRequest } from "next/server";
import { GATEWAY_URL, SESSION_COOKIE } from "@/lib/server/config";
import { rejectCrossSite } from "@/lib/server/csrf";

/**
 * BFF：瀏覽器呼叫 /api/bff/<path>，這裡從 httpOnly cookie 取出 JWT，轉給 Gateway 的 /api/<path>。
 * <ul>
 *   <li>token 從不經過前端 JavaScript</li>
 *   <li>非 GET 請求必須帶自訂 header 且同源，擋 CSRF</li>
 *   <li>Gateway 回 401（token 過期或被撤銷）時一併清掉 cookie</li>
 * </ul>
 */
async function forward(request: NextRequest, ctx: RouteContext<"/api/bff/[...path]">) {
  const { path } = await ctx.params;
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (!token) {
    return error(401, "UNAUTHENTICATED", "請重新登入");
  }
  if (request.method !== "GET") {
    const rejected = rejectCrossSite(request);
    if (rejected) return rejected;
  }
  if (path.some((segment) => segment === "." || segment === "..")) {
    return error(400, "INVALID_PATH", "路徑不合法");
  }

  const target = `${GATEWAY_URL}/api/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;
  const headers: Record<string, string> = { authorization: `Bearer ${token}` };
  const contentType = request.headers.get("content-type");
  if (contentType) headers["content-type"] = contentType;

  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.text(),
      cache: "no-store",
    });
  } catch {
    return error(502, "GATEWAY_UNREACHABLE", "無法連線到伺服器，請稍後再試");
  }

  const response = new NextResponse(await upstream.text(), {
    status: upstream.status,
    headers: pick(upstream.headers, ["content-type", "retry-after", "x-trace-id"]),
  });
  if (upstream.status === 401) {
    response.cookies.delete(SESSION_COOKIE);
  }
  return response;
}

function pick(source: Headers, names: string[]): Headers {
  const headers = new Headers();
  for (const name of names) {
    const value = source.get(name);
    if (value) headers.set(name, value);
  }
  return headers;
}

function error(status: number, code: string, message: string) {
  return NextResponse.json({ code, message, data: null }, { status });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;
