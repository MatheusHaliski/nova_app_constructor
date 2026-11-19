import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { COOKIE_REFRESH } from "@/lib/config";
import { rejectCrossOrigin } from "@/lib/csrf";
import { backendFetch, clearSessionCookies } from "@/lib/session";

export async function POST(request: Request) {
  const blocked = rejectCrossOrigin(request);
  if (blocked) return blocked;
  const refreshToken = (await cookies()).get(COOKIE_REFRESH)?.value;
  if (refreshToken) {
    await backendFetch("auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }) });
  }
  const response = NextResponse.json({ ok: true });
  clearSessionCookies(response);
  return response;
}
