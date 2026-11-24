import { NextResponse } from "next/server";
import { API_URL } from "@/lib/config";

/** Leitura pública de um projeto por link de compartilhamento (arquivo e download); só GET, sem sessão. */
export async function GET(request: Request, ctx: RouteContext<"/api/shared/[...path]">) {
  const { path } = await ctx.params;
  const [token, action, ...rest] = path;
  if (!/^[A-Za-z0-9_-]{20,100}$/.test(token) || rest.length > 0 || !["file", "download", "tree"].includes(action ?? "")) {
    return NextResponse.json({ title: "Rota não permitida" }, { status: 404 });
  }
  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}/api/v1/shared/${token}/${action}${new URL(request.url).search}`, { cache: "no-store" });
  } catch {
    return NextResponse.json({ title: "Serviço indisponível" }, { status: 503 });
  }
  const out = new Headers({ "Referrer-Policy": "no-referrer", "X-Robots-Tag": "noindex" });
  for (const name of ["content-type", "content-disposition"]) {
    const value = upstream.headers.get(name);
    if (value) out.set(name, value);
  }
  return new NextResponse(upstream.body, { status: upstream.status, headers: out });
}
