"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Alert, Button } from "@/components/ui/ui";
import type { OpenFile, PreviewBundle } from "./insights-types";

/*
 * Prévia das telas: o código do frontend da versão (com as edições manuais) roda num iframe isolado, com respostas de
 * exemplo da API. Dá para trocar a largura, o estado dos dados, quem acessa e, no modo edição, clicar num elemento para
 * abrir o arquivo na linha certa ou dar dois cliques num texto para alterá-lo direto no código.
 */

type View = "data" | "empty" | "loading" | "error" | "slow" | "many" | "long";
type Role = "user" | "visitor";
type Request = { method: string; path: string; status: number };

const WIDTHS = [
  { id: "desktop", label: "Computador", width: "100%" },
  { id: "tablet", label: "Tablet", width: "768px" },
  { id: "phone", label: "Celular", width: "390px" },
] as const;
const VIEWS: { id: View; label: string }[] = [
  { id: "data", label: "Com dados" },
  { id: "empty", label: "Sem dados" },
  { id: "loading", label: "Carregando" },
  { id: "many", label: "Muitos itens" },
  { id: "long", label: "Textos longos" },
  { id: "slow", label: "API lenta" },
  { id: "error", label: "Erro da API" },
];

export function PreviewPanel({
  load,
  editable,
  onOpenFile,
  onSaveText,
}: {
  load: () => Promise<PreviewBundle | { error: string }>;
  editable: boolean;
  onOpenFile?: OpenFile;
  onSaveText?: (file: string, line: number, before: string, after: string) => Promise<string | null>;
}) {
  const frame = useRef<HTMLIFrameElement>(null);
  const [bundle, setBundle] = useState<PreviewBundle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [route, setRoute] = useState("/");
  const [width, setWidth] = useState<(typeof WIDTHS)[number]["id"]>("desktop");
  const [view, setView] = useState<View>("data");
  const [role, setRole] = useState<Role>("user");
  const [editing, setEditing] = useState(false);
  const [selected, setSelected] = useState<{ file: string; line: number; tag: string; text: string } | null>(null);
  const [requests, setRequests] = useState<Request[]>([]);
  const [notice, setNotice] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const state = useRef({ route, view, role, editing, bundle });

  useEffect(() => {
    let alive = true;
    void load().then((r) => {
      if (!alive) return;
      if ("error" in r) {
        setError(r.error);
      } else {
        setBundle(r);
        setError(null);
      }
    });
    return () => {
      alive = false;
    };
  }, [load, reloadKey]);

  const render = useCallback(() => {
    const s = state.current;
    if (!s.bundle || !frame.current?.contentWindow) return;
    frame.current.contentWindow.postMessage(
      { type: "nova:render", framework: s.bundle.framework, sources: s.bundle.sources, mocks: s.bundle.mocks, pages: s.bundle.pages, root: s.bundle.root, route: s.route, view: s.view, role: s.role, editing: s.editing },
      "*",
    );
  }, []);

  useEffect(() => {
    state.current = { route, view, role, editing, bundle };
    render();
  }, [render, route, view, role, editing, bundle]);

  useEffect(() => {
    async function onMessage(e: MessageEvent) {
      if (e.source !== frame.current?.contentWindow) return;
      const d = e.data as { type: string; [k: string]: unknown };
      if (d.type === "nova:ready") render();
      if (d.type === "nova:navigate") setRoute(String(d.href));
      if (d.type === "nova:request") setRequests((r) => [...r.slice(-7), { method: String(d.method), path: String(d.path), status: Number(d.status) }]);
      if (d.type === "nova:select") setSelected({ file: String(d.file), line: Number(d.line), tag: String(d.tag), text: String(d.text) });
      if (d.type === "nova:edit" && onSaveText) {
        const problem = await onSaveText(String(d.file), Number(d.line), String(d.before), String(d.after));
        setNotice(problem ? { tone: "danger", text: problem } : { tone: "success", text: "Texto alterado no código; a prévia foi atualizada." });
        if (!problem) setReloadKey((k) => k + 1);
      }
    }
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [render, onSaveText]);

  if (error) return <Alert tone="warning">{error}</Alert>;
  if (!bundle) return <p className="text-sm text-muted">Montando a prévia…</p>;
  if (!bundle.framework) return <Alert tone="info">Este projeto não tem frontend, então não há telas para mostrar.</Alert>;

  const srcDoc = `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body${editing ? " data-nova-editing" : ""} style="margin:0"><div id="root"></div><script src="${window.location.origin}/preview/runtime.js"></script></body></html>`;
  const pageWidth = WIDTHS.find((w) => w.id === width)!.width;
  const segmented = (on: boolean) => `rounded-md px-3 py-1.5 text-xs font-semibold ${on ? "bg-surface text-text shadow-sm" : "text-muted hover:text-text"}`;

  return (
    <div className="grid gap-4 xl:grid-cols-[14rem_minmax(0,1fr)_16rem]">
      <nav aria-label="Páginas do frontend" className="flex min-w-0 flex-col gap-1 rounded-2xl border border-border bg-surface p-2">
        <p className="px-2 pb-1 pt-1 text-[11px] font-bold uppercase tracking-wide text-muted">Páginas · {bundle.pages.length}</p>
        {bundle.pages.map((p) => {
          const href = p.route.replace(/\[[^\]]+\]/g, "1").replace(/:[^/]+/g, "1");
          return (
            <button key={p.route} onClick={() => setRoute(href)} aria-current={route.split("?")[0] === href ? "page" : undefined}
              className={`rounded-lg px-2 py-2 text-left text-xs ${route.split("?")[0] === href ? "bg-primary/10 font-semibold" : "hover:bg-surface-2"}`}>
              <span className="block text-sm">{p.title ?? p.route}</span>
              <span className="block truncate font-mono text-muted">{p.route}</span>
            </button>
          );
        })}
      </nav>

      <section aria-label="Prévia" className="flex min-w-0 flex-col items-center gap-2">
        <div className="flex w-full flex-wrap items-center justify-between gap-2">
          <div role="group" aria-label="Largura" className="flex rounded-lg bg-surface-2 p-1">
            {WIDTHS.map((w) => (
              <button key={w.id} aria-pressed={width === w.id} onClick={() => setWidth(w.id)} className={segmented(width === w.id)}>{w.label}</button>
            ))}
          </div>
          <div className="flex flex-wrap gap-2">
            {editable && (
              <Button variant={editing ? "primary" : "secondary"} icon="form" aria-pressed={editing} onClick={() => setEditing((e) => !e)}>
                {editing ? "Editando" : "Editar na prévia"}
              </Button>
            )}
            <Button variant="secondary" onClick={() => setReloadKey((k) => k + 1)}>Recarregar</Button>
          </div>
        </div>
        {editing && <p className="w-full text-xs text-muted">Clique num elemento para ver onde ele está no código; dê dois cliques num texto para alterá-lo.</p>}
        {notice && <div className="w-full"><Alert tone={notice.tone}>{notice.text}</Alert></div>}
        <div className="w-full max-w-full overflow-hidden rounded-xl border border-border bg-white shadow-sm" style={{ width: pageWidth }}>
          <div className="flex items-center gap-2 border-b border-border bg-surface-2 px-3 py-2">
            <span aria-hidden="true" className="flex gap-1"><i className="h-2.5 w-2.5 rounded-full bg-border" /><i className="h-2.5 w-2.5 rounded-full bg-border" /><i className="h-2.5 w-2.5 rounded-full bg-border" /></span>
            <input aria-label="Endereço da página" value={route} onChange={(e) => setRoute(e.target.value)}
              className="min-w-0 flex-1 rounded-md bg-surface px-2 py-1 font-mono text-xs" />
          </div>
          <iframe key={`${reloadKey}-${editing}`} ref={frame} title="Prévia da tela" sandbox="allow-scripts" srcDoc={srcDoc} className="block h-[38rem] w-full bg-white" />
        </div>
        <p className="text-xs text-muted">Versão {bundle.version} · dados de exemplo gerados a partir das entidades{bundle.truncated ? " · parte dos arquivos ficou de fora por tamanho" : ""}</p>
      </section>

      <aside className="flex min-w-0 flex-col gap-3">
        <div className="rounded-2xl border border-border bg-surface p-3">
          <p className="mb-2 text-sm font-bold">Estado dos dados</p>
          <div className="flex flex-wrap gap-1.5">
            {VIEWS.map((v) => (
              <button key={v.id} aria-pressed={view === v.id} onClick={() => setView(v.id)}
                className={`rounded-full border px-3 py-1 text-xs font-semibold ${view === v.id ? "border-primary bg-primary/10 text-primary" : "border-border text-muted"}`}>{v.label}</button>
            ))}
          </div>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-3">
          <p className="mb-2 text-sm font-bold">Quem acessa</p>
          <div className="flex gap-1.5">
            {([["user", "Pessoa logada"], ["visitor", "Visitante"]] as const).map(([id, label]) => (
              <button key={id} aria-pressed={role === id} onClick={() => setRole(id)}
                className={`rounded-full border px-3 py-1 text-xs font-semibold ${role === id ? "border-primary bg-primary/10 text-primary" : "border-border text-muted"}`}>{label}</button>
            ))}
          </div>
          <p className="mt-2 text-xs text-muted">Visitante recebe 401 nas rotas que exigem login.</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-3">
          <p className="mb-2 text-sm font-bold">Chamadas à API</p>
          {requests.length === 0 ? <p className="text-xs text-muted">Nenhuma nesta tela.</p> : (
            <ul className="flex flex-col gap-1 font-mono text-[11px]">
              {requests.map((r, i) => <li key={i} className="truncate">{r.method} {r.path} → {r.status}</li>)}
            </ul>
          )}
        </div>
        {selected && (
          <div className="rounded-2xl border border-primary/40 bg-primary/5 p-3 text-xs">
            <p className="font-bold">&lt;{selected.tag}&gt; {selected.text && `“${selected.text.slice(0, 60)}”`}</p>
            <p className="mt-1 break-all font-mono text-muted">{selected.file}:{selected.line}</p>
            {onOpenFile && (
              <button className="mt-2 inline-flex items-center gap-1 font-semibold text-primary" onClick={() => onOpenFile(selected.file, selected.line)}>
                <NovaIcon name="tree" size={14} /> Abrir no editor
              </button>
            )}
          </div>
        )}
      </aside>
    </div>
  );
}
