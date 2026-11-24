export type Problem = { detail?: string; title?: string };
export type Result<T> = { ok: true; data: T } | { ok: false; problem: Problem };

/** Chamada do navegador a uma rota do próprio Next (ponte autenticada ou link compartilhado). */
export async function call<T>(url: string, method = "GET", body?: unknown): Promise<Result<T>> {
  let res: Response;
  try {
    res = await fetch(url, {
      method,
      headers: body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    return { ok: false, problem: { detail: "Sem conexão com o servidor." } };
  }
  if (res.ok) {
    const text = await res.text();
    return { ok: true, data: (text ? JSON.parse(text) : null) as T };
  }
  return { ok: false, problem: (await res.json().catch(() => ({}))) as Problem };
}

export const errorText = (problem: Problem) => problem.detail ?? problem.title ?? "Não foi possível concluir.";
