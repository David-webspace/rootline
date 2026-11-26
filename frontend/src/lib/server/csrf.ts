import "server-only";
import { NextResponse, type NextRequest } from "next/server";
import { CSRF_HEADER } from "./config";

/**
 * 寫入型請求的同源檢查。跨站頁面可以送表單或 <img>，但送不出自訂 header（會觸發 CORS preflight 被擋），
 * 再加上 Origin 比對做雙重確認。登入也要檢查，否則攻擊者能把受害者登入成攻擊者的帳號（login CSRF）。
 */
export function rejectCrossSite(request: NextRequest): NextResponse | null {
  const origin = request.headers.get("origin");
  const sameOrigin = origin === null || origin === request.nextUrl.origin;
  if (request.headers.get(CSRF_HEADER) === "1" && sameOrigin) {
    return null;
  }
  return NextResponse.json({ code: "CSRF_REJECTED", message: "請求來源不被接受", data: null }, { status: 403 });
}
