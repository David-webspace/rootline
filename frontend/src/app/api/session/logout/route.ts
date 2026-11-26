import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE } from "@/lib/server/config";
import { rejectCrossSite } from "@/lib/server/csrf";

export async function POST(request: NextRequest) {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;
  const response = NextResponse.json({ code: "OK", message: "signed out", data: null });
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
