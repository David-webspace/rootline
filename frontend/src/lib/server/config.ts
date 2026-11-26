import "server-only";

/** Gateway 位址。只在伺服器端使用，瀏覽器永遠只跟 Next.js 自己的 /api/* 溝通。 */
export const GATEWAY_URL = (process.env.ROOTLINE_GATEWAY_URL ?? "http://127.0.0.1:9000").replace(/\/$/, "");

/** 存 JWT 的 httpOnly cookie 名稱。前端 JavaScript 讀不到它，XSS 也偷不走 token。 */
export const SESSION_COOKIE = "rootline_session";

/** 非 GET 請求必須帶這個 header。跨站表單送不出自訂 header，藉此擋 CSRF。 */
export const CSRF_HEADER = "x-rootline-csrf";
