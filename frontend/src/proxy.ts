import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "rootline_session";

/**
 * 頁面層的第一道導向：沒有有效 session 的 /dashboard 請求導去登入；已登入的人造訪登入頁直接進 dashboard。
 * 這只是使用體驗，不是授權：BFF 與後端每次都會再檢查。
 * （Proxy 建議不依賴共用模組，所以這裡自行做最小的 exp 檢查。）
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const signedIn = isUnexpired(request.cookies.get(SESSION_COOKIE)?.value);

  if (pathname.startsWith("/dashboard") && !signedIn) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname + search);
    const response = NextResponse.redirect(login);
    response.cookies.delete(SESSION_COOKIE);
    return response;
  }
  if ((pathname === "/login" || pathname === "/register") && signedIn) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }
  return NextResponse.next();
}

function isUnexpired(token: string | undefined): boolean {
  const payload = token?.split(".")[1];
  if (!payload) return false;
  try {
    const exp = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/"))).exp;
    return typeof exp === "number" && exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export const config = {
  matcher: ["/dashboard/:path*", "/login", "/register"],
};
