"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { NovaIcon, type NovaIconName } from "@/components/icons/NovaIcon";
import { CatalogIcon } from "@/components/tech/CatalogIcon";
import { Alert, Button, Card, Field, Input, Modal } from "@/components/ui/ui";
import type { Catalog, FileEntry, ProjectSummary, VersionSummary } from "@/lib/catalog-types";
import type { IconData } from "@/lib/techs";
import { formatBytes, formatDate } from "./format";
import { SharePanel } from "@/components/community/SharePanel";
import { CompareVersions } from "./CompareVersions";
import { ImprovementsPanel } from "./ImprovementsPanel";
import { InsightsPanel } from "./InsightsPanel";
import type { Insights, PreviewBundle } from "./insights-types";
import { PreviewPanel } from "./PreviewPanel";
import { VisibilityBadge } from "@/components/community/VisibilityBadge";
import { StackBadges } from "./StackBadges";

/*
 * Página do projeto (Objetivo 2): ficha técnica, árvore de pastas e arquivos com edição, versões
 * (cada alteração cria uma versão; restaurar nunca apaga histórico) e download do projeto, de uma pasta ou versão.
 */

type Tab = "overview" | "improvements" | "preview" | "files" | "versions" | "share" | "settings";
type Problem = { detail?: string };
type Warning = { path: string; line: number; kind: string };
type ChangeResult = { version: number; secretWarnings: Warning[] };

const TABS: { id: Tab; label: string; icon: NovaIconName }[] = [
  { id: "overview", label: "Painel", icon: "layers" },
  { id: "improvements", label: "Melhorias", icon: "shieldCheck" },
  { id: "preview", label: "Prévia", icon: "screens" },
  { id: "files", label: "Arquivos", icon: "tree" },
  { id: "versions", label: "Versões", icon: "history" },
  { id: "share", label: "Compartilhar", icon: "share" },
  { id: "settings", label: "Configurações", icon: "sliders" },
];

const ORIGIN: Record<VersionSummary["origin"], string> = { GENERATOR: "Gerador", USER: "Edição", RESTORE: "Restauração", FORK: "Fork", IMPROVEMENT: "Melhoria" };

async function request<T>(path: string, method = "GET", body?: unknown): Promise<{ ok: true; data: T } | { ok: false; problem: Problem }> {
  const res = await fetch(`/api/backend/${path}`, {
    method,
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.ok) {
    const text = await res.text();
    return { ok: true, data: (text ? JSON.parse(text) : null) as T };
  }
  return { ok: false, problem: (await res.json().catch(() => ({}))) as Problem };
}

export function ProjectWorkspace({
  project: initialProject,
  initialTree,
  initialVersions,
  readme,
  catalog,
  icons,
  initialTab = "overview",
}: {
  initialTab?: "overview" | "share" | "improvements" | "preview";
  project: ProjectSummary;
  initialTree: FileEntry[];
  initialVersions: VersionSummary[];
  readme: string | null;
  catalog: Catalog;
  icons: Record<string, IconData>;
}) {
  const [project, setProject] = useState(initialProject);
  const [tree, setTree] = useState(initialTree);
  const [versions, setVersions] = useState(initialVersions);
  const [tab, setTab] = useState<Tab>(initialTab);
  const [message, setMessage] = useState<{ tone: "success" | "danger" | "warning"; text: string } | null>(null);
  const base = `projects/${project.id}`;
  const readOnly = project.status !== "ACTIVE";

  const refresh = useCallback(async () => {
    const [p, t, v] = await Promise.all([request<ProjectSummary>(base), request<FileEntry[]>(`${base}/tree`), request<VersionSummary[]>(`${base}/versions`)]);
    if (p.ok) setProject(p.data);
    if (t.ok) setTree(t.data);
    if (v.ok) setVersions(v.data);
  }, [base]);

  const afterChange = useCallback(
    async (result: { ok: true; data: ChangeResult } | { ok: false; problem: Problem }, success: string) => {
      if (!result.ok) {
        setMessage({ tone: "danger", text: result.problem.detail ?? "Não foi possível concluir." });
        return false;
      }
      await refresh();
      const warnings = result.data.secretWarnings;
      setMessage(
        warnings.length
          ? { tone: "warning", text: `${success} Atenção: possível credencial em ${warnings.map((w) => `${w.path}:${w.line} (${w.kind})`).join(", ")}. Mova o valor para uma variável de ambiente.` }
          : { tone: "success", text: success },
      );
      return true;
    },
    [refresh],
  );

  const [insights, setInsights] = useState<Insights | null>(null);
  const [focus, setFocus] = useState<string | undefined>(undefined);
  const [fileTarget, setFileTarget] = useState<{ path: string; line?: number; nonce: number } | null>(null);

  useEffect(() => {
    let alive = true;
    if (tab !== "overview" && tab !== "improvements") return;
    void request<Insights>(`${base}/insights`).then((r) => {
      if (alive && r.ok) setInsights(r.data);
    });
    return () => {
      alive = false;
    };
  }, [tab, base, project.currentVersion]);

  const openFile = useCallback((path: string, line?: number) => {
    setFileTarget({ path, line, nonce: Date.now() });
    setTab("files");
  }, []);

  const openImprovement = useCallback((id?: string) => {
    setFocus(id);
    setTab("improvements");
  }, []);

  const loadPreview = useCallback(async (): Promise<PreviewBundle | { error: string }> => {
    const r = await request<PreviewBundle>(`${base}/preview-bundle`);
    return r.ok ? r.data : { error: r.problem.detail ?? "Não foi possível montar a prévia." };
  }, [base]);

  /** Edição direta na prévia: troca o texto no arquivo, perto da linha indicada, e salva como nova versão. */
  const saveText = useCallback(async (file: string, line: number, before: string, after: string): Promise<string | null> => {
    const current = await request<{ content: string | null; version: number }>(`${base}/file?path=${encodeURIComponent(file)}`);
    if (!current.ok || current.data.content === null) return "Não consegui abrir o arquivo da tela.";
    const lines = current.data.content.split("\n");
    const target = before.trim();
    const order = [line - 1, ...Array.from({ length: 12 }, (_, i) => line + i), ...Array.from({ length: 6 }, (_, i) => line - 2 - i)];
    const at = order.find((i) => i >= 0 && i < lines.length && target && lines[i].includes(target));
    if (at === undefined) return "Não achei esse texto no código (ele pode vir da API). Edite pelo arquivo.";
    lines[at] = lines[at].replace(target, after.trim().replace(/[{}<>]/g, ""));
    const saved = await request<ChangeResult>(`${base}/file`, "PUT", { path: file, content: lines.join("\n"), baseVersion: current.data.version });
    if (!saved.ok) return saved.problem.detail ?? "Não foi possível salvar.";
    await refresh();
    return null;
  }, [base, refresh]);

  return (
    <div className="mx-auto max-w-7xl">
      <header className="mb-5 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs text-muted">
            <Link href="/projetos" className="hover:text-text">
              Projetos
            </Link>{" "}
            / {project.slug}
          </p>
          <h1 className="mt-1 flex flex-wrap items-center gap-3 text-2xl font-bold tracking-tight">
            {project.name}
            <span className="rounded-full bg-surface-2 px-2 py-0.5 text-xs font-semibold text-muted">v{project.currentVersion}</span>
            <VisibilityBadge visibility={project.visibility} />
            {readOnly && <span className="rounded-full bg-warning/15 px-2 py-0.5 text-xs font-semibold text-warning">{project.status === "ARCHIVED" ? "Arquivado" : "Na lixeira"}</span>}
          </h1>
          {project.description && <p className="mt-1 text-sm text-muted">{project.description}</p>}
          {project.forkedFromLabel && <p className="mt-1 text-xs text-muted">Fork de {project.forkedFromLabel}</p>}
          <div className="mt-2">
            <StackBadges spec={project.spec} catalog={catalog} icons={icons} />
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <a href={`/api/backend/${base}/download`} className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-text hover:brightness-110">
            <NovaIcon name="download" size={16} /> Baixar ZIP
          </a>
          <a href={`/api/backend/${base}/download?format=tar.gz`} className="inline-flex items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-2">
            tar.gz
          </a>
        </div>
      </header>

      <nav className="mb-5 flex gap-1 overflow-x-auto border-b border-border" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={`-mb-px flex shrink-0 items-center gap-2 border-b-2 px-4 py-2 text-sm ${tab === t.id ? "border-primary font-semibold" : "border-transparent text-muted hover:text-text"}`}
          >
            <NovaIcon name={t.icon} size={16} gradient={tab === t.id} />
            {t.label}
          </button>
        ))}
      </nav>

      {message && (
        <div className="mb-4">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}

      {tab === "overview" && (
        <div className="flex flex-col gap-5">
          {insights ? <InsightsPanel insights={insights} projectId={project.id} onOpenFile={openFile} onOpenImprovement={openImprovement} />
            : <p className="text-sm text-muted">Analisando o projeto…</p>}
          <Overview project={project} readme={readme} catalog={catalog} icons={icons} />
        </div>
      )}
      {tab === "improvements" && (insights ? (
        <ImprovementsPanel key={insights.version} insights={insights} projectId={project.id} readOnly={readOnly} initialId={focus} onOpenFile={openFile}
          onApplied={async (text) => {
            await refresh();
            const r = await request<Insights>(`${base}/insights`);
            if (r.ok) setInsights(r.data);
            setMessage({ tone: "success", text });
          }} />
      ) : <p className="text-sm text-muted">Analisando o projeto…</p>)}
      {tab === "preview" && <PreviewPanel load={loadPreview} editable={!readOnly} onOpenFile={openFile} onSaveText={saveText} />}
      {tab === "files" && <Files key={fileTarget?.nonce ?? 0} base={base} tree={tree} version={project.currentVersion} readOnly={readOnly} afterChange={afterChange}
        initialPath={fileTarget?.path} initialLine={fileTarget?.line} />}
      {tab === "versions" && (
        <div className="flex flex-col gap-5">
          <Versions base={base} versions={versions} current={project.currentVersion} readOnly={readOnly} afterChange={afterChange} onLabeled={refresh} />
          {versions.length > 1 && <CompareVersions projectId={project.id} versions={versions} />}
        </div>
      )}
      {tab === "share" && <SharePanel projectId={project.id} projectName={project.name} disabled={readOnly} onVisibility={(visibility) => setProject((p) => ({ ...p, visibility }))} />}
      {tab === "settings" && <Settings project={project} onChanged={setProject} onMessage={setMessage} />}
    </div>
  );
}

/* ---------------- Visão geral ---------------- */

function Overview({ project, readme, catalog, icons }: { project: ProjectSummary; readme: string | null; catalog: Catalog; icons: Record<string, IconData> }) {
  const s = project.spec;
  const backend = catalog.backends.find((b) => b.id === s.backend.id);
  const rows: { label: string; value: string; icon?: string; name?: string }[] = [
    { label: "Backend", value: `${backend?.name ?? s.backend.id} ${s.backend.version ?? ""}`, icon: backend?.icon, name: backend?.name },
    { label: "Linguagem", value: `${catalog.languages.find((l) => l.id === s.backend.language)?.name ?? ""} ${s.backend.languageVersion ?? ""}`, icon: catalog.languages.find((l) => l.id === s.backend.language)?.icon },
    { label: "Build", value: backend?.buildTools.find((b) => b.id === s.backend.buildTool)?.name ?? "—", icon: backend?.buildTools.find((b) => b.id === s.backend.buildTool)?.icon },
  ];
  const frontend = catalog.frontends.find((f) => f.id === s.frontend?.id);
  if (frontend) rows.push({ label: "Frontend", value: `${frontend.name} ${s.frontend?.version ?? ""}`, icon: frontend.icon });
  const db = catalog.databases.find((d) => d.id === s.database?.id);
  if (db) rows.push({ label: "Banco", value: `${db.name} ${s.database?.version ?? ""}`, icon: db.icon });
  for (const c of s.connectors ?? []) {
    const conn = catalog.connectors.find((x) => x.id === c);
    if (conn) rows.push({ label: "Conector", value: conn.name, icon: conn.icon });
  }
  const ai = catalog.aiProviders.find((a) => a.id === s.ai?.provider);
  if (ai) rows.push({ label: "IA externa", value: `${ai.name} · ${s.ai?.model ?? ""}`, icon: ai.icon });

  return (
    <div className="grid gap-5 lg:grid-cols-[360px_1fr] [&>*]:min-w-0">
      <div className="flex flex-col gap-5">
        <Card>
          <h2 className="mb-3 font-bold">Ficha técnica</h2>
          <ul className="flex flex-col gap-2">
            {rows.map((r, i) => (
              <li key={`${r.label}-${i}`} className="flex items-center gap-3">
                <CatalogIcon icons={icons} slug={r.icon ?? ""} name={r.value} size={32} />
                <span className="min-w-0">
                  <span className="block text-[11px] font-bold uppercase tracking-wide text-muted">{r.label}</span>
                  <span className="block truncate text-sm font-semibold">{r.value}</span>
                </span>
              </li>
            ))}
          </ul>
          <dl className="mt-4 grid grid-cols-2 gap-2 text-sm">
            <Info label="Modo" value={s.mode === "COPILOT" ? "Copilot total" : "Assistido"} />
            <Info label="Tipo" value={project.type === "APP" ? "App" : "Web App"} />
            <Info label="Arquivos" value={String(project.fileCount)} />
            <Info label="Tamanho" value={formatBytes(project.sizeBytes)} />
            <Info label="Criado em" value={formatDate(project.createdAt)} />
            <Info label="Atualizado" value={formatDate(project.updatedAt)} />
          </dl>
        </Card>
        {(s.features?.length > 0 || s.screens?.length > 0) && (
          <Card>
            {s.features?.length > 0 && (
              <>
                <h2 className="mb-2 font-bold">Recursos</h2>
                <ul className="mb-4 flex flex-wrap gap-1.5">
                  {s.features.map((f) => (
                    <li key={f} className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
                      {catalog.features.find((x) => x.id === f)?.name ?? f}
                    </li>
                  ))}
                </ul>
              </>
            )}
            {s.screens?.length > 0 && (
              <>
                <h2 className="mb-2 font-bold">Telas ({s.screens.length})</h2>
                <ol className="list-decimal pl-5 text-sm text-muted">
                  {s.screens.map((x) => (
                    <li key={x}>{x}</li>
                  ))}
                </ol>
              </>
            )}
          </Card>
        )}
      </div>
      <Card>
        <h2 className="mb-3 flex items-center gap-2 font-bold">
          <NovaIcon name="book" size={18} /> README.md
        </h2>
        <pre className="overflow-x-auto whitespace-pre-wrap break-words text-sm leading-relaxed">{readme ?? "Este projeto não tem README.md."}</pre>
      </Card>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-surface-2 px-3 py-2">
      <dt className="text-[11px] text-muted">{label}</dt>
      <dd className="font-semibold">{value}</dd>
    </div>
  );
}

/* ---------------- Arquivos ---------------- */

type Node = { name: string; path: string; children: Node[]; file?: FileEntry };

function buildTree(entries: FileEntry[]): Node[] {
  const root: Node = { name: "", path: "", children: [] };
  for (const entry of entries) {
    let node = root;
    const parts = entry.path.split("/");
    parts.forEach((part, i) => {
      const path = parts.slice(0, i + 1).join("/");
      let child = node.children.find((c) => c.name === part);
      if (!child) {
        child = { name: part, path, children: [] };
        node.children.push(child);
      }
      if (i === parts.length - 1) child.file = entry;
      node = child;
    });
  }
  const sort = (n: Node) => {
    n.children.sort((a, b) => Number(!!a.file) - Number(!!b.file) || a.name.localeCompare(b.name));
    n.children.forEach(sort);
  };
  sort(root);
  return root.children;
}

function Files({
  base,
  tree,
  version,
  readOnly,
  afterChange,
  initialPath,
  initialLine,
}: {
  base: string;
  tree: FileEntry[];
  version: number;
  readOnly: boolean;
  afterChange: (r: { ok: true; data: ChangeResult } | { ok: false; problem: Problem }, success: string) => Promise<boolean>;
  initialPath?: string;
  initialLine?: number;
}) {
  const editor = useRef<HTMLTextAreaElement>(null);
  const [filter, setFilter] = useState("");
  const [open, setOpen] = useState<Set<string>>(() => {
    const dirs = new Set(tree.map((e) => e.path.split("/")[0]));
    if (initialPath) {
      const parts = initialPath.split("/");
      for (let i = 1; i < parts.length; i++) dirs.add(parts.slice(0, i).join("/"));
    }
    return dirs;
  });

  const [selected, setSelected] = useState<string | null>(initialPath && tree.some((e) => e.path === initialPath) ? initialPath : tree.find((e) => e.path === "README.md")?.path ?? null);
  const [content, setContent] = useState<string | null>(null);
  const [binary, setBinary] = useState(false);
  const [draft, setDraft] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [dialog, setDialog] = useState<null | { kind: "new" } | { kind: "move"; path: string } | { kind: "delete"; path: string }>(null);

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    return q ? tree.filter((e) => e.path.toLowerCase().includes(q)) : tree;
  }, [tree, filter]);
  const nodes = useMemo(() => buildTree(filtered), [filtered]);
  const dirty = draft !== null && draft !== content;
  const selectedEntry = tree.find((e) => e.path === selected);

  useEffect(() => {
    const el = editor.current;
    if (!el || content === null || !initialLine || selected !== initialPath) return;
    const lines = content.split("\n");
    const start = lines.slice(0, initialLine - 1).reduce((n, l) => n + l.length + 1, 0);
    el.focus();
    el.setSelectionRange(start, start + (lines[initialLine - 1]?.length ?? 0));
    el.scrollTop = Math.max(0, (initialLine - 5) * 18);
  }, [content, initialLine, initialPath, selected]);

  useEffect(() => {
    let cancelled = false;
    if (!selected) return;
    request<{ content: string | null; binary: boolean }>(`${base}/file?path=${encodeURIComponent(selected)}`).then((r) => {
      if (cancelled) return;
      setContent(r.ok ? r.data.content : null);
      setBinary(r.ok ? r.data.binary : false);
      setDraft(null);
    });
    return () => {
      cancelled = true;
    };
  }, [base, selected, version]);

  function select(path: string) {
    if (dirty && !window.confirm("Há alterações não salvas neste arquivo. Descartar?")) return;
    setContent(null);
    setSelected(path);
  }

  async function save() {
    if (!selected || draft === null) return;
    setSaving(true);
    const ok = await afterChange(await request<ChangeResult>(`${base}/file`, "PUT", { path: selected, content: draft, baseVersion: version }), `Arquivo salvo.`);
    setSaving(false);
    if (ok) {
      setContent(draft);
      setDraft(null);
    }
  }

  function toggle(path: string) {
    setOpen((s) => {
      const next = new Set(s);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
  }

  const renderNodes = (list: Node[], depth: number) =>
    list.map((n) => {
      const isOpen = open.has(n.path) || filter.trim() !== "";
      return (
        <li key={n.path}>
          <div
            className={`group flex items-center gap-1 rounded-md pr-1 text-sm ${selected === n.path ? "bg-primary/10 font-semibold" : "hover:bg-surface-2"}`}
            style={{ paddingLeft: 8 + depth * 14 }}
          >
            <button className="flex min-w-0 flex-1 items-center gap-2 py-1 text-left" onClick={() => (n.file ? select(n.path) : toggle(n.path))} aria-expanded={n.file ? undefined : isOpen}>
              <NovaIcon name={n.file ? "form" : isOpen ? "chevronDown" : "chevronRight"} size={14} className="shrink-0 text-muted" />
              {!n.file && <NovaIcon name="folder" size={14} className="shrink-0 text-muted" />}
              <span className="truncate">{n.name}</span>
            </button>
            {!n.file && (
              <a href={`/api/backend/${base}/download?path=${encodeURIComponent(n.path)}`} title={`Baixar a pasta ${n.name}`} aria-label={`Baixar a pasta ${n.name}`} className="hidden rounded p-1 text-muted hover:text-text group-hover:block">
                <NovaIcon name="download" size={14} />
              </a>
            )}
            {!readOnly && (
              <>
                <button title="Renomear ou mover" aria-label={`Renomear ou mover ${n.name}`} onClick={() => setDialog({ kind: "move", path: n.path })} className="hidden rounded p-1 text-muted hover:text-text group-hover:block">
                  <NovaIcon name="form" size={14} />
                </button>
                <button title="Excluir" aria-label={`Excluir ${n.name}`} onClick={() => setDialog({ kind: "delete", path: n.path })} className="hidden rounded p-1 text-muted hover:text-danger group-hover:block">
                  <NovaIcon name="trash" size={14} />
                </button>
              </>
            )}
          </div>
          {!n.file && isOpen && <ul>{renderNodes(n.children, depth + 1)}</ul>}
        </li>
      );
    });

  return (
    <div className="grid gap-4 lg:grid-cols-[320px_1fr] [&>*]:min-w-0">
      <Card className="p-3">
        <div className="mb-2 flex gap-2">
          <Input aria-label="Filtrar arquivos" placeholder="Filtrar arquivos" value={filter} onChange={(e) => setFilter(e.target.value)} />
          {!readOnly && (
            <Button variant="secondary" onClick={() => setDialog({ kind: "new" })} title="Novo arquivo" aria-label="Novo arquivo">
              +
            </Button>
          )}
        </div>
        <ul className="max-h-[65vh] overflow-auto">{renderNodes(nodes, 0)}</ul>
        <p className="mt-2 text-[11px] text-muted">{tree.length} arquivos</p>
      </Card>
      <Card className="p-0">
        {!selected ? (
          <p className="p-6 text-sm text-muted">Escolha um arquivo na árvore.</p>
        ) : (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-2">
              <p className="min-w-0 truncate text-sm">
                <code className="font-semibold">{selected}</code>
                {selectedEntry && <span className="ml-2 text-xs text-muted">{formatBytes(selectedEntry.size)}</span>}
                {dirty && <span className="ml-2 text-xs font-semibold text-warning">● não salvo</span>}
              </p>
              <div className="flex gap-2">
                {!readOnly && !binary && (
                  <Button onClick={save} disabled={!dirty || saving}>
                    {saving ? "Salvando…" : "Salvar"}
                  </Button>
                )}
                {dirty && (
                  <Button variant="secondary" onClick={() => setDraft(null)}>
                    Descartar
                  </Button>
                )}
              </div>
            </div>
            {binary ? (
              <p className="p-6 text-sm text-muted">Arquivo binário: baixe o projeto para abri-lo.</p>
            ) : content === null ? (
              <p className="p-6 text-sm text-muted">Carregando…</p>
            ) : (
              <textarea
                ref={editor}
                aria-label={`Conteúdo de ${selected}`}
                spellCheck={false}
                readOnly={readOnly}
                value={draft ?? content}
                onChange={(e) => setDraft(e.target.value)}
                onKeyDown={(e) => {
                  if ((e.ctrlKey || e.metaKey) && e.key === "s") {
                    e.preventDefault();
                    void save();
                  }
                }}
                className="block h-[65vh] w-full resize-none bg-transparent p-4 font-mono text-[13px] leading-relaxed focus:outline-none"
              />
            )}
          </>
        )}
      </Card>

      {dialog?.kind === "new" && (
        <PathDialog
          title="Novo arquivo"
          label="Caminho do arquivo"
          initial=""
          placeholder="backend/src/main/resources/exemplo.txt"
          submit="Criar"
          onClose={() => setDialog(null)}
          onSubmit={async (path) => {
            const ok = await afterChange(await request<ChangeResult>(`${base}/file`, "PUT", { path, content: "", baseVersion: version, createOnly: true }), "Arquivo criado.");
            if (ok) {
              setDialog(null);
              setSelected(path.replace(/^\/+/, ""));
            }
          }}
        />
      )}
      {dialog?.kind === "move" && (
        <PathDialog
          title="Renomear ou mover"
          label="Novo caminho"
          initial={dialog.path}
          submit="Mover"
          onClose={() => setDialog(null)}
          onSubmit={async (to) => {
            const from = dialog.path;
            const ok = await afterChange(await request<ChangeResult>(`${base}/files/move`, "POST", { from, to, baseVersion: version }), "Movido.");
            if (ok) {
              setDialog(null);
              if (selected && (selected === from || selected.startsWith(`${from}/`))) setSelected(to + selected.slice(from.length));
            }
          }}
        />
      )}
      {dialog?.kind === "delete" && (
        <Modal title="Excluir?" onClose={() => setDialog(null)}>
          <p className="mb-4 text-sm">
            <code>{dialog.path}</code> será removido da versão atual. As versões anteriores continuam no histórico e podem ser restauradas.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDialog(null)}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                const path = dialog.path;
                const ok = await afterChange(await request<ChangeResult>(`${base}/files/delete`, "POST", { path, baseVersion: version }), "Excluído.");
                if (ok) {
                  setDialog(null);
                  if (selected && (selected === path || selected.startsWith(`${path}/`))) setSelected(null);
                }
              }}
            >
              Excluir
            </Button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function PathDialog({ title, label, initial, placeholder, submit, onClose, onSubmit }: { title: string; label: string; initial: string; placeholder?: string; submit: string; onClose: () => void; onSubmit: (path: string) => Promise<void> }) {
  const [path, setPath] = useState(initial);
  const [pending, setPending] = useState(false);
  async function handle(e: FormEvent) {
    e.preventDefault();
    setPending(true);
    await onSubmit(path.trim());
    setPending(false);
  }
  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={handle} className="flex flex-col gap-4">
        <Field label={label} htmlFor="path-input">
          <Input id="path-input" value={path} placeholder={placeholder} onChange={(e) => setPath(e.target.value)} autoFocus />
        </Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={pending || !path.trim() || path.trim() === initial}>
            {submit}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/* ---------------- Versões ---------------- */

function Versions({
  base,
  versions,
  current,
  readOnly,
  afterChange,
  onLabeled,
}: {
  base: string;
  versions: VersionSummary[];
  current: number;
  readOnly: boolean;
  afterChange: (r: { ok: true; data: ChangeResult } | { ok: false; problem: Problem }, success: string) => Promise<boolean>;
  onLabeled: () => Promise<void>;
}) {
  const [editing, setEditing] = useState<number | null>(null);
  const [label, setLabel] = useState("");
  const [restoring, setRestoring] = useState<number | null>(null);

  async function saveLabel(number: number) {
    await request(`${base}/versions/${number}/label`, "PUT", { label });
    setEditing(null);
    await onLabeled();
  }

  return (
    <Card>
      <p className="mb-4 text-sm text-muted">Cada alteração cria uma versão. Restaurar cria uma nova versão igual à escolhida; nada do histórico é apagado.</p>
      <ol className="flex flex-col">
        {versions.map((v) => (
          <li key={v.number} className="flex flex-wrap items-center gap-3 border-b border-border py-3 last:border-0">
            <span className={`flex h-8 w-10 shrink-0 items-center justify-center rounded-full text-xs font-bold ${v.number === current ? "nova-gradient-bar text-white" : "bg-surface-2"}`}>v{v.number}</span>
            <div className="min-w-0 flex-1">
              {editing === v.number ? (
                <form
                  className="flex gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    void saveLabel(v.number);
                  }}
                >
                  <Input aria-label="Nome da versão" value={label} maxLength={120} onChange={(e) => setLabel(e.target.value)} autoFocus />
                  <Button type="submit">Salvar</Button>
                </form>
              ) : (
                <p className="truncate text-sm font-semibold">{v.label ?? "Sem nome"}</p>
              )}
              <p className="text-xs text-muted">
                {ORIGIN[v.origin]} · {formatDate(v.createdAt)} · {v.fileCount} arquivos · {formatBytes(v.sizeBytes)}
                {v.number === current && " · atual"}
              </p>
            </div>
            <div className="flex gap-1">
              <Button
                variant="ghost"
                onClick={() => {
                  setEditing(v.number);
                  setLabel(v.label ?? "");
                }}
              >
                Nomear
              </Button>
              <a href={`/api/backend/${base}/download?version=${v.number}`} className="inline-flex items-center rounded-lg px-3 py-2 text-sm font-semibold hover:bg-surface-2">
                Baixar
              </a>
              {!readOnly && v.number !== current && (
                <Button variant="secondary" disabled={restoring === v.number} onClick={() => setRestoring(v.number)}>
                  Restaurar
                </Button>
              )}
            </div>
          </li>
        ))}
      </ol>
      {restoring !== null && (
        <Modal title={`Restaurar a versão ${restoring}?`} onClose={() => setRestoring(null)}>
          <p className="mb-4 text-sm">Será criada a versão {current + 1} com os mesmos arquivos da versão {restoring}. As versões atuais continuam no histórico.</p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setRestoring(null)}>
              Cancelar
            </Button>
            <Button
              onClick={async () => {
                const n = restoring;
                setRestoring(null);
                await afterChange(await request<ChangeResult>(`${base}/versions/${n}/restore`, "POST"), `Versão ${n} restaurada.`);
              }}
            >
              Restaurar
            </Button>
          </div>
        </Modal>
      )}
    </Card>
  );
}

/* ---------------- Configurações ---------------- */

function Settings({
  project,
  onChanged,
  onMessage,
}: {
  project: ProjectSummary;
  onChanged: (p: ProjectSummary) => void;
  onMessage: (m: { tone: "success" | "danger" | "warning"; text: string } | null) => void;
}) {
  const router = useRouter();
  const base = `projects/${project.id}`;
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? "");
  const [confirm, setConfirm] = useState("");
  const [deleting, setDeleting] = useState(false);

  async function act(path: string, method: string, body: unknown, success: string) {
    const result = await request<ProjectSummary>(path, method, body);
    if (result.ok) {
      onChanged(result.data);
      onMessage({ tone: "success", text: success });
    } else {
      onMessage({ tone: "danger", text: result.problem.detail ?? "Não foi possível concluir." });
    }
  }

  return (
    <div className="flex max-w-3xl flex-col gap-5">
      <Card>
        <h2 className="mb-3 font-bold">Nome e descrição</h2>
        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            void act(base, "PATCH", { name, description }, "Projeto atualizado.");
          }}
        >
          <Field label="Nome" htmlFor="project-name">
            <Input id="project-name" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Descrição" htmlFor="project-description">
            <Input id="project-description" value={description} maxLength={500} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <div>
            <Button type="submit" disabled={!name.trim()}>
              Salvar alterações
            </Button>
          </div>
        </form>
      </Card>
      <Card>
        <h2 className="mb-1 font-bold">Situação</h2>
        <p className="mb-3 text-sm text-muted">Projetos arquivados ou na lixeira ficam somente leitura. A lixeira pode ser desfeita.</p>
        <div className="flex flex-wrap gap-2">
          {project.status === "ACTIVE" ? (
            <>
              <Button variant="secondary" onClick={() => act(`${base}/archive`, "POST", undefined, "Projeto arquivado.")}>
                Arquivar
              </Button>
              <Button variant="secondary" icon="trash" onClick={() => act(base, "DELETE", undefined, "Projeto movido para a lixeira.")}>
                Mover para a lixeira
              </Button>
            </>
          ) : (
            <Button onClick={() => act(`${base}/activate`, "POST", undefined, "Projeto reativado.")}>{project.status === "ARCHIVED" ? "Desarquivar" : "Restaurar da lixeira"}</Button>
          )}
        </div>
      </Card>
      {project.status === "TRASHED" && (
        <Card className="border-danger/40">
          <h2 className="mb-1 font-bold text-danger">Excluir definitivamente</h2>
          <p className="mb-3 text-sm text-muted">
            Apaga o projeto e todas as versões. Não pode ser desfeito. Baixe o ZIP antes, se quiser guardar uma cópia. Para confirmar, digite <strong>{project.name}</strong>.
          </p>
          <div className="flex flex-wrap gap-2">
            <Input aria-label="Nome do projeto para confirmar" value={confirm} onChange={(e) => setConfirm(e.target.value)} className="max-w-xs" />
            <Button
              variant="danger"
              disabled={confirm !== project.name || deleting}
              onClick={async () => {
                setDeleting(true);
                const result = await request(`${base}/delete-permanently`, "POST", { confirmName: confirm });
                if (result.ok) {
                  router.push("/projetos?status=TRASHED");
                  router.refresh();
                } else {
                  setDeleting(false);
                  onMessage({ tone: "danger", text: result.problem.detail ?? "Não foi possível excluir." });
                }
              }}
            >
              Excluir definitivamente
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
