import "server-only";
import { cookies } from "next/headers";
import type { NextResponse } from "next/server";
import { API_URL, COOKIE_ACCESS, COOKIE_REFRESH, secureCookies } from "./config";

export type Role = "USER" | "ADMIN";

export type User = {
  id: string;
  name: string;
  username: string;
  email: string;
  roles: Role[];
  enabled: boolean;
  theme: "SYSTEM" | "LIGHT" | "DARK";
  language: "pt-BR" | "en-US" | "es-ES";
  createdAt: string;
};

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
};

export type ApiResult<T> = { ok: true; status: number; data: T } | { ok: false; status: number; problem: Problem };

export type Problem = { title?: string; detail?: string; status?: number; errors?: Record<string, string> };

const REFRESH_MAX_AGE = 60 * 60 * 24 * 7;

/** Grava os tokens em cookies httpOnly: o JavaScript do navegador nunca vê os tokens. */
export function setSessionCookies(response: NextResponse, tokens: Pick<TokenResponse, "accessToken" | "refreshToken" | "expiresIn">) {
  const base = { httpOnly: true, secure: secureCookies, sameSite: "lax" as const, path: "/" };
  response.cookies.set(COOKIE_ACCESS, tokens.accessToken, { ...base, maxAge: tokens.expiresIn });
  response.cookies.set(COOKIE_REFRESH, tokens.refreshToken, { ...base, maxAge: REFRESH_MAX_AGE });
}

export function clearSessionCookies(response: NextResponse) {
  response.cookies.delete(COOKIE_ACCESS);
  response.cookies.delete(COOKIE_REFRESH);
}

/** Chama a API do backend com o access token da sessão atual. */
export async function api<T>(path: string, init: RequestInit = {}): Promise<ApiResult<T>> {
  const token = (await cookies()).get(COOKIE_ACCESS)?.value;
  return backendFetch<T>(path, init, token);
}

export async function backendFetch<T>(path: string, init: RequestInit = {}, token?: string): Promise<ApiResult<T>> {
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  let response: Response;
  try {
    response = await fetch(`${API_URL}/api/v1/${path}`, { ...init, headers, cache: "no-store" });
  } catch {
    return { ok: false, status: 503, problem: { title: "Serviço indisponível", detail: "Não foi possível falar com o servidor do NOVA." } };
  }
  const text = await response.text();
  const body = text ? safeJson(text) : null;
  if (response.ok) return { ok: true, status: response.status, data: body as T };
  return { ok: false, status: response.status, problem: (body as Problem) ?? { title: response.statusText } };
}

/** Usuário da sessão atual, ou null se não houver sessão válida. */
export async function getCurrentUser(): Promise<User | null> {
  const result = await api<User>("me");
  return result.ok ? result.data : null;
}

export function isAdmin(user: User | null): boolean {
  return Boolean(user?.roles.includes("ADMIN"));
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return { detail: text };
  }
}

/** Aplica as preferências de interface do perfil (tema e idioma) nos cookies de renderização. */
export function setPreferenceCookies(response: NextResponse, user: Pick<User, "theme" | "language">) {
  const options = { sameSite: "lax" as const, path: "/", maxAge: 60 * 60 * 24 * 365 };
  response.cookies.set("nova_theme", user.theme, options);
  response.cookies.set("nova_lang", user.language, options);
}
