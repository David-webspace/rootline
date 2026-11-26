import "server-only";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { decodeClaims, type SessionClaims } from "./claims";
import { SESSION_COOKIE } from "./config";

/**
 * 目前登入的使用者（Data Access Layer 的入口）。
 * `use cache: private` 讓同一個請求、同一個瀏覽器內重複呼叫只讀一次 cookie，結果只存在瀏覽器端快取。
 * 呼叫的元件必須放在 <Suspense> 裡（Cache Components 的規定：讀 cookie 不能進靜態外殼）。
 */
export async function getCurrentUser(): Promise<SessionClaims> {
  "use cache: private";
  const token = (await cookies()).get(SESSION_COOKIE)?.value;
  const claims = decodeClaims(token);
  if (!claims) {
    redirect("/login");
  }
  return claims;
}
