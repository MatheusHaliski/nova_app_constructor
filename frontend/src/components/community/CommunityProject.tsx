"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState, type FormEvent } from "react";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { formatBytes, formatDate } from "@/components/projects/format";
import { StackBadges } from "@/components/projects/StackBadges";
import { Alert, Button, Card, Field, Input, Modal, Textarea } from "@/components/ui/ui";
import type { Catalog, FileEntry } from "@/lib/catalog-types";
import { LICENSES, REPORT_REASONS, type CommunityCard, type CommunityComment, type ReportReason } from "@/lib/community-types";
import type { IconData } from "@/lib/techs";
import { call, errorText } from "./client";
import { VisibilityBadge } from "./VisibilityBadge";

/*
 * Página de um projeto compartilhado, somente leitura. Na rede (logado) permite favoritar, fazer fork, comentar e
 * denunciar; por link (sem login) mostra só o projeto, os arquivos visíveis e o download.
 */

type FileContent = { path: string; size: number; binary: boolean; content: string | null };

export function CommunityProject({
  apiBase,
  card: initialCard,
  tree,
  initialFile,
  initialComments,
  catalog,
  icons,
  interactive,
}: {
  apiBase: string;
  card: CommunityCard;
  tree: FileEntry[];
  initialFile: FileContent | null;
  initialComments: CommunityComment[];
  catalog: Catalog | null;
  icons: Record<string, IconData>;
  interactive: boolean;
}) {
  const router = useRouter();
  const [card, setCard] = useState(initialCard);
  const [file, setFile] = useState<FileContent | null>(initialFile);
  const [filter, setFilter] = useState("");
  const [comments, setComments] = useState(initialComments);
  const [body, setBody] = useState("");
  const [message, setMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [reporting, setReporting] = useState<{ commentId: number | null } | null>(null);
  const [confirmFork, setConfirmFork] = useState(false);
  const [suggesting, setSuggesting] = useState(false);

  const visible = useMemo(() => tree.filter((f) => f.path.toLowerCase().includes(filter.trim().toLowerCase())), [tree, filter]);
  const fail = (text: string) => setMessage({ tone: "danger", text });
  const forkBlocked = card.license === "ALL_RIGHTS_RESERVED";

  async function open(path: string) {
    const result = await call<FileContent>(`${apiBase}/file?path=${encodeURIComponent(path)}`);
    if (result.ok) setFile(result.data);
    else fail(errorText(result.problem));
  }

  async function toggleStar() {
    const result = await call<CommunityCard>(`${apiBase}/star`, card.starredByMe ? "DELETE" : "PUT");
    if (result.ok) setCard(result.data);
    else fail(errorText(result.problem));
  }

  async function fork() {
    setBusy(true);
    const result = await call<{ id: string }>(`${apiBase}/fork`, "POST");
    setBusy(false);
    setConfirmFork(false);
    if (result.ok) {
      router.push(`/projetos/${result.data.id}`);
      router.refresh();
    } else fail(errorText(result.problem));
  }

  async function reloadComments() {
    const result = await call<CommunityComment[]>(`${apiBase}/comments`);
    if (result.ok) {
      setComments(result.data);
      setCard((c) => ({ ...c, comments: result.data.length }));
    }
  }

  async function addComment(e: FormEvent) {
    e.preventDefault();
    const result = await call(`${apiBase}/comments`, "POST", { body });
    if (result.ok) {
      setBody("");
      setMessage(null);
      await reloadComments();
    } else fail(errorText(result.problem));
  }

  async function removeComment(id: number) {
    const result = await call(`${apiBase}/comments/${id}`, "DELETE");
    if (result.ok) await reloadComments();
    else fail(errorText(result.problem));
  }

  return (
    <div className="mx-auto max-w-7xl">
      <header className="mb-5 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs text-muted">
            {interactive ? (
              <Link href="/explorar" className="hover:text-text">
                Rede NOVA
              </Link>
            ) : (
              "Projeto compartilhado por link"
            )}{" "}
            / @{card.author} / {card.slug}
          </p>
          <h1 className="mt-1 flex flex-wrap items-center gap-3 text-2xl font-bold tracking-tight">
            {card.name}
            <span className="rounded-full bg-surface-2 px-2 py-0.5 text-xs font-semibold text-muted">v{card.version}</span>
            <VisibilityBadge visibility={card.visibility} />
          </h1>
          {card.description && <p className="mt-1 text-sm text-muted">{card.description}</p>}
          <p className="mt-1 text-xs text-muted">
            por <strong className="text-text">@{card.author}</strong>
            {card.license && <> · Licença: {LICENSES[card.license].label}</>}
            {card.forkedFromLabel && <> · Fork de {card.forkedFromLabel}</>} · {card.fileCount} arquivos · {formatBytes(card.sizeBytes)}
          </p>
          {catalog && (
            <div className="mt-2">
              <StackBadges spec={card.spec} catalog={catalog} icons={icons} />
            </div>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {interactive && (
            <>
              <Button variant="secondary" icon="star" aria-pressed={card.starredByMe} onClick={toggleStar}>
                {card.starredByMe ? "Favoritado" : "Favoritar"} · {card.stars}
              </Button>
              {card.mine ? (
                <Link href={`/projetos/${card.id}`} className="inline-flex items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-2">
                  <NovaIcon name="sliders" size={16} /> Gerenciar
                </Link>
              ) : (
                <>
                  <Button variant="secondary" icon="git" disabled={forkBlocked} title={forkBlocked ? "O autor reservou todos os direitos." : undefined} onClick={() => setConfirmFork(true)}>
                    Fork · {card.forks}
                  </Button>
                  {!forkBlocked && (
                    <Link href={`/novo-app?remix=${card.id}`} className="inline-flex items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-2">
                      <NovaIcon name="sparkle" size={16} /> Remixar
                    </Link>
                  )}
                  <Button variant="secondary" icon="form" onClick={() => setSuggesting(true)}>Sugerir alteração</Button>
                </>
              )}
            </>
          )}
          <a href={`${apiBase}/download`} className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-text hover:brightness-110">
            <NovaIcon name="download" size={16} /> Baixar ZIP
          </a>
          {interactive && !card.mine && (
            <Button variant="ghost" icon="alert" onClick={() => setReporting({ commentId: null })}>
              Denunciar
            </Button>
          )}
        </div>
      </header>

      {message && (
        <div className="mb-4">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-[18rem_minmax(0,1fr)]">
        <Card className="min-w-0 !p-3">
          <Input aria-label="Filtrar arquivos" placeholder="Filtrar arquivos" value={filter} onChange={(e) => setFilter(e.target.value)} />
          <ul className="mt-2 max-h-[28rem] overflow-auto text-xs">
            {visible.map((f) => (
              <li key={f.path}>
                <button
                  onClick={() => open(f.path)}
                  title={f.path}
                  className={`block w-full truncate rounded px-2 py-1 text-left font-mono hover:bg-surface-2 ${file?.path === f.path ? "bg-primary/10 font-semibold" : ""}`}
                >
                  {f.path}
                </button>
              </li>
            ))}
            {visible.length === 0 && <li className="px-2 py-3 text-muted">Nenhum arquivo.</li>}
          </ul>
        </Card>
        <Card className="min-w-0 !p-0">
          {file ? (
            <>
              <p className="flex items-center justify-between gap-2 border-b border-border px-4 py-2 text-xs text-muted">
                <span className="truncate font-mono">{file.path}</span>
                <span className="shrink-0">{formatBytes(file.size)} · somente leitura</span>
              </p>
              {file.binary || file.content === null ? (
                <p className="p-4 text-sm text-muted">Arquivo binário: baixe o projeto para abrir.</p>
              ) : (
                <pre aria-label={`Conteúdo de ${file.path}`} className="max-h-[28rem] overflow-auto p-4 font-mono text-xs leading-relaxed">
                  {file.content}
                </pre>
              )}
            </>
          ) : (
            <p className="p-4 text-sm text-muted">Escolha um arquivo para ver o conteúdo.</p>
          )}
        </Card>
      </div>

      {interactive && (
        <Card className="mt-5 max-w-3xl">
          <h2 className="mb-3 font-bold">Comentários ({comments.length})</h2>
          <ul className="flex flex-col gap-3">
            {comments.map((c) => (
              <li key={c.id} className="rounded-xl border border-border p-3">
                <p className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted">
                  <span>
                    <strong className="text-text">{c.author ? `@${c.author}` : "usuário removido"}</strong> · {formatDate(c.createdAt)}
                  </span>
                  <span className="flex gap-2">
                    {!c.mine && (
                      <button className="hover:text-text" onClick={() => setReporting({ commentId: c.id })}>
                        Denunciar
                      </button>
                    )}
                    {c.canDelete && (
                      <button className="text-danger hover:underline" onClick={() => removeComment(c.id)}>
                        Remover
                      </button>
                    )}
                  </span>
                </p>
                <p className="mt-1 whitespace-pre-wrap text-sm">{c.body}</p>
              </li>
            ))}
            {comments.length === 0 && <li className="text-sm text-muted">Seja a primeira pessoa a comentar.</li>}
          </ul>
          <form onSubmit={addComment} className="mt-4 flex flex-col gap-2">
            <Textarea aria-label="Novo comentário" rows={3} maxLength={2000} value={body} onChange={(e) => setBody(e.target.value)} placeholder="Escreva um comentário…" />
            <div>
              <Button type="submit" disabled={!body.trim()}>
                Comentar
              </Button>
            </div>
          </form>
        </Card>
      )}

      {confirmFork && (
        <Modal title={`Fazer fork de ${card.name}?`} onClose={() => setConfirmFork(false)}>
          <p className="mb-4 text-sm text-muted">
            Uma cópia privada entra nos seus projetos, com a origem <strong>@{card.author}/{card.slug}</strong> registrada. Arquivos que o autor ocultou não são copiados.
            {card.license && <> Licença do original: {LICENSES[card.license].label}.</>}
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setConfirmFork(false)}>
              Cancelar
            </Button>
            <Button onClick={fork} disabled={busy}>
              {busy ? "Copiando…" : "Fazer fork"}
            </Button>
          </div>
        </Modal>
      )}

      {suggesting && (
        <SuggestDialog apiBase={apiBase} file={file && !file.binary ? file : null} onClose={() => setSuggesting(false)}
          onDone={() => {
            setSuggesting(false);
            setMessage({ tone: "success", text: "Sugestão enviada. O dono do projeto revisa na aba Melhorias e você é avisado se ela for aplicada." });
          }} />
      )}

      {reporting && (
        <ReportDialog
          apiBase={apiBase}
          commentId={reporting.commentId}
          onClose={() => setReporting(null)}
          onDone={() => {
            setReporting(null);
            setMessage({ tone: "success", text: "Denúncia enviada. A moderação vai analisar." });
          }}
        />
      )}
    </div>
  );
}

function ReportDialog({ apiBase, commentId, onClose, onDone }: { apiBase: string; commentId: number | null; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState<ReportReason | "">("");
  const [details, setDetails] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const result = await call(`${apiBase}/reports`, "POST", { commentId, reason, details });
    if (result.ok) onDone();
    else setError(errorText(result.problem));
  }

  return (
    <Modal title={commentId ? "Denunciar comentário" : "Denunciar projeto"} onClose={onClose}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        {error && <Alert tone="danger">{error}</Alert>}
        <Field label="Motivo" htmlFor="report-reason">
          <select id="report-reason" value={reason} onChange={(e) => setReason(e.target.value as ReportReason | "")} className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm">
            <option value="">Escolha…</option>
            {(Object.keys(REPORT_REASONS) as ReportReason[]).map((r) => (
              <option key={r} value={r}>
                {REPORT_REASONS[r]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Detalhes (opcional)" htmlFor="report-details" hint="Diga onde está o problema: arquivo, linha ou trecho.">
          <Textarea id="report-details" rows={3} maxLength={1000} value={details} onChange={(e) => setDetails(e.target.value)} />
        </Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={!reason}>
            Enviar denúncia
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function SuggestDialog({ apiBase, file, onClose, onDone }: { apiBase: string; file: FileContent | null; onClose: () => void; onDone: () => void }) {
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [content, setContent] = useState(file?.content ?? "");
  const [error, setError] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    const changed = file && content !== file.content;
    const result = await call(`${apiBase}/suggestions`, "POST", { title, body, files: changed ? [{ path: file.path, content }] : [] });
    if (result.ok) onDone();
    else setError(errorText(result.problem));
  }
  return (
    <Modal title="Sugerir alteração ao dono" onClose={onClose}>
      <form onSubmit={submit} className="flex flex-col gap-3">
        {error && <Alert tone="danger">{error}</Alert>}
        <Field label="Título" htmlFor="sug-title"><Input id="sug-title" maxLength={120} value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
        <Field label="O que e por quê" htmlFor="sug-body"><Textarea id="sug-body" rows={3} maxLength={2000} value={body} onChange={(e) => setBody(e.target.value)} /></Field>
        {file ? (
          <Field label={`Alteração em ${file.path} (opcional)`} htmlFor="sug-content" hint="Edite o arquivo aberto; se não mudar nada, a sugestão vai só com o texto.">
            <Textarea id="sug-content" rows={8} value={content} onChange={(e) => setContent(e.target.value)} className="font-mono text-xs" />
          </Field>
        ) : <p className="text-xs text-muted">Abra um arquivo antes para sugerir uma mudança no código dele.</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button>
          <Button type="submit" disabled={!title.trim() || !body.trim()}>Enviar sugestão</Button>
        </div>
      </form>
    </Modal>
  );
}
