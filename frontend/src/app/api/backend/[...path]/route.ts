import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { API_URL, COOKIE_ACCESS } from "@/lib/config";
import { rejectCrossOrigin } from "@/lib/csrf";

/**
 * Ponte autenticada para os componentes do navegador: anexa o access token (cookie httpOnly) e repassa
 * a chamada ao backend. Só libera os grupos de rota usados pela interface.
 */
const ALLOWED = ["me", "admin", "catalog", "generator", "copilot", "projects", "community"];

async function forward(request: Request, ctx: RouteContext<"/api/backend/[...path]">) {
  if (request.method !== "GET") {
    const blocked = rejectCrossOrigin(request);
    if (blocked) return blocked;
  }
  const { path } = await ctx.params;
  if (!ALLOWED.includes(path[0]) || path.some((p) => p === ".." || p.includes("/"))) {
    return NextResponse.json({ title: "Rota não permitida" }, { status: 404 });
  }
  const token = (await cookies()).get(COOKIE_ACCESS)?.value;
  const url = new URL(request.url);
  const headers = new Headers({ Accept: "application/json" });
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const body = request.method === "GET" || request.method === "HEAD" ? undefined : await request.text();
  if (body) headers.set("Content-Type", "application/json");
  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}/api/v1/${path.map(encodeURIComponent).join("/")}${url.search}`, {
      method: request.method,
      headers,
      body,
      cache: "no-store",
    });
  } catch {
    return NextResponse.json({ title: "Serviço indisponível" }, { status: 503 });
  }
  const out = new Headers();
  for (const name of ["content-type", "content-disposition"]) {
    const value = upstream.headers.get(name);
    if (value) out.set(name, value);
  }
  return new NextResponse(upstream.status === 204 ? null : upstream.body, { status: upstream.status, headers: out });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;
