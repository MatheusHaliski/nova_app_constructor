import type { Settings } from "./types";

export type Problem = { title?: string; detail?: string; errors?: Record<string, string> };

/** Chama a ponte autenticada /api/backend/* e devolve o JSON ou o ProblemDetail. */
export async function callMe<T = Settings>(path: string, method: string, body?: unknown): Promise<{ ok: true; data: T } | { ok: false; problem: Problem }> {
  const res = await fetch(`/api/backend/me${path}`, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.ok) {
    const text = await res.text();
    return { ok: true, data: (text ? JSON.parse(text) : null) as T };
  }
  const problem = (await res.json().catch(() => ({}))) as Problem;
  return { ok: false, problem };
}
