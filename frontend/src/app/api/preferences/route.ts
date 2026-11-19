import { NextResponse } from "next/server";
import { rejectCrossOrigin } from "@/lib/csrf";
import { COOKIE_LANG, COOKIE_THEME, parseLang, parseTheme } from "@/lib/prefs";
import { api } from "@/lib/session";

/** Salva tema e idioma no perfil (backend) e nos cookies usados para renderizar a interface. */
export async function PUT(request: Request) {
  const blocked = rejectCrossOrigin(request);
  if (blocked) return blocked;
  const input = (await request.json()) as { theme?: string; language?: string };
  const theme = parseTheme(input.theme);
  const language = parseLang(input.language);
  const result = await api("me/preferences", { method: "PUT", body: JSON.stringify({ theme, language }) });
  if (!result.ok) return NextResponse.json(result.problem, { status: result.status });
  const response = NextResponse.json(result.data);
  const options = { sameSite: "lax" as const, path: "/", maxAge: 60 * 60 * 24 * 365 };
  response.cookies.set(COOKIE_THEME, theme, options);
  response.cookies.set(COOKIE_LANG, language, options);
  return response;
}
