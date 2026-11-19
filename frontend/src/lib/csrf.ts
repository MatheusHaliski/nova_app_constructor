import "server-only";
import { NextResponse } from "next/server";

/** Requisições que alteram estado só são aceitas da própria origem (defesa extra além do SameSite=Lax). */
export function rejectCrossOrigin(request: Request): NextResponse | null {
  const origin = request.headers.get("origin");
  if (!origin) return null;
  const host = request.headers.get("x-forwarded-host") ?? request.headers.get("host");
  try {
    if (new URL(origin).host === host) return null;
  } catch {
    // origem inválida
  }
  return NextResponse.json({ title: "Origem não permitida" }, { status: 403 });
}
