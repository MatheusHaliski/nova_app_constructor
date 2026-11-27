import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/*
 * Controle de acesso no servidor, antes de renderizar:
 * - rotas privadas sem sessão vão para /login?next=…;
 * - access token vencido é renovado com o refresh token (rotação) e os cookies novos seguem
 *   tanto para o navegador quanto para a própria requisição;
 * - quem já está logado não vê /login nem /cadastro.
 * A autorização por perfil (ADMIN) é conferida de novo nas páginas e sempre no backend.
 */

const ACCESS = "nova_at";
const REFRESH = "nova_rt";
const API_URL = process.env.NOVA_API_URL ?? "http://localhost:8080";
const PRIVATE = ["/novo-app", "/projetos", "/painel", "/configuracoes", "/admin", "/tecnologias", "/em-breve", "/notificacoes", "/feed", "/explorar", "/estrelas", "/compartilhados", "/comunidade", "/pessoas", "/perfil", "/api/backend", "/api/preferences"];
const GUEST_ONLY = ["/login", "/cadastro"];

function matches(path: string, prefixes: string[]) {
  return prefixes.some((p) => path === p || path.startsWith(`${p}/`));
}

function isFresh(token: string | undefined): boolean {
  if (!token) return false;
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" && payload.exp * 1000 > Date.now() + 30_000;
  } catch {
    return false;
  }
}

export async function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const isPrivate = matches(path, PRIVATE);
  const access = request.cookies.get(ACCESS)?.value;
  const refresh = request.cookies.get(REFRESH)?.value;

  let session = isFresh(access);
  let renewed: { accessToken: string; refreshToken: string; expiresIn: number } | null = null;

  if (!session && refresh && (isPrivate || matches(path, GUEST_ONLY))) {
    try {
      const res = await fetch(`${API_URL}/api/v1/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "User-Agent": request.headers.get("user-agent") ?? "" },
        body: JSON.stringify({ refreshToken: refresh }),
        cache: "no-store",
      });
      if (res.ok) {
        renewed = await res.json();
        session = true;
      }
    } catch {
      // backend fora do ar: segue sem renovar
    }
  }

  if (!session && isPrivate) {
    if (path.startsWith("/api/")) {
      return NextResponse.json({ title: "Não autenticado", detail: "Faça login para continuar." }, { status: 401 });
    }
    const login = new URL("/login", request.url);
    login.searchParams.set("next", path);
    const response = NextResponse.redirect(login);
    if (refresh) {
      response.cookies.delete(ACCESS);
      response.cookies.delete(REFRESH);
    }
    return response;
  }

  if (session && matches(path, GUEST_ONLY)) {
    const response = NextResponse.redirect(new URL("/painel", request.url));
    if (renewed) setCookies(response, renewed);
    return response;
  }

  if (!renewed) return NextResponse.next();

  // Repassa os tokens novos para a renderização desta mesma requisição.
  request.cookies.set(ACCESS, renewed.accessToken);
  request.cookies.set(REFRESH, renewed.refreshToken);
  const headers = new Headers(request.headers);
  headers.set("cookie", request.cookies.toString());
  const response = NextResponse.next({ request: { headers } });
  setCookies(response, renewed);
  return response;
}

function setCookies(response: NextResponse, t: { accessToken: string; refreshToken: string; expiresIn: number }) {
  const base = { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "lax" as const, path: "/" };
  response.cookies.set(ACCESS, t.accessToken, { ...base, maxAge: t.expiresIn });
  response.cookies.set(REFRESH, t.refreshToken, { ...base, maxAge: 60 * 60 * 24 * 7 });
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|brand/|api/auth/).*)"],
};
