import { NextResponse } from "next/server";
import { rejectCrossOrigin } from "@/lib/csrf";
import { backendFetch, setPreferenceCookies, setSessionCookies, type TokenResponse } from "@/lib/session";

export async function POST(request: Request) {
  const blocked = rejectCrossOrigin(request);
  if (blocked) return blocked;
  const result = await backendFetch<TokenResponse>("auth/register", { method: "POST", body: await request.text() });
  if (!result.ok) return NextResponse.json(result.problem, { status: result.status });
  const response = NextResponse.json({ user: result.data.user }, { status: 201 });
  setSessionCookies(response, result.data);
  setPreferenceCookies(response, result.data.user);
  return response;
}
