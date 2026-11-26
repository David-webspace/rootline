import { NextResponse, type NextRequest } from "next/server";
import { GATEWAY_URL } from "@/lib/server/config";
import { rejectCrossSite } from "@/lib/server/csrf";

/** 自助註冊：直接轉給 Gateway。成功後使用者再自行登入（註冊不自動建立 session）。 */
export async function POST(request: NextRequest) {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;
  try {
    const upstream = await fetch(`${GATEWAY_URL}/api/auth/register`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: await request.text(),
      cache: "no-store",
    });
    return new NextResponse(await upstream.text(), {
      status: upstream.status,
      headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" },
    });
  } catch {
    return NextResponse.json({ code: "GATEWAY_UNREACHABLE", message: "無法連線到伺服器，請稍後再試", data: null }, { status: 502 });
  }
}
