import { NextResponse } from "next/server";
import { rejectCrossOrigin } from "@/lib/csrf";
import { backendFetch, setPreferenceCookies, setSessionCookies, type TokenResponse } from "@/lib/session";

export async function POST(request: Request) {
  const blocked = rejectCrossOrigin(request);
  if (blocked) return blocked;
  const result = await backendFetch<TokenResponse>("auth/login", {
    method: "POST",
    body: await request.text(),
    // O navegador da pessoa identifica a sessão em Configurações > Sessões.
    headers: { "User-Agent": request.headers.get("user-agent") ?? "" },
  });
  if (!result.ok) return NextResponse.json(result.problem, { status: result.status });
  const response = NextResponse.json({ user: result.data.user });
  setSessionCookies(response, result.data);
  setPreferenceCookies(response, result.data.user);
  return response;
}
