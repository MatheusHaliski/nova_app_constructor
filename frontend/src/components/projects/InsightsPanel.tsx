"use client";

import { useState, type FormEvent } from "react";
import { NovaIcon, type NovaIconName } from "@/components/icons/NovaIcon";
import { Alert, Button, Card, LgpdTag, Textarea } from "@/components/ui/ui";
import type { Insights, OpenFile, Operation, Proposal, StageStatus } from "./insights-types";

/*
 * Painel do projeto: saúde e caminho até publicar no topo, e janelas segmentadas com cada camada vista a fundo.
 * Cada dado aparece numa única janela; o que vira ação leva para a aba Melhorias.
 */

type Window = "layers" | "api" | "data" | "screens" | "flows" | "requirements" | "privacy" | "a11y" | "deps" | "ask";

const OPS: { id: Operation; label: string }[] = [
  { id: "LIST", label: "Listar" },
  { id: "READ", label: "Ver" },
  { id: "CREATE", label: "Criar" },
  { id: "UPDATE", label: "Editar" },
  { id: "DELETE", label: "Excluir" },
];

const STATUS: Record<StageStatus, { label: string; cls: string }> = {
  PASS: { label: "Passou", cls: "border-success/30 bg-success/10 text-success" },
  WARN: { label: "Atenção", cls: "border-warning/40 bg-warning/10 text-warning" },
  FAIL: { label: "Lacunas", cls: "border-danger/40 bg-danger/10 text-danger" },
  NOT_RUN: { label: "Não executada", cls: "border-border bg-surface-2 text-muted" },
  NOT_APPLICABLE: { label: "Não se aplica", cls: "border-border bg-surface-2 text-muted" },
};

const ACCESS: Record<string, string> = { PUBLIC: "Público", AUTHENTICATED: "Logado" };

function access(a: string) {
  return ACCESS[a] ?? a;
}

export function InsightsPanel({ insights, projectId, onOpenFile, onOpenImprovement }: {
  insights: Insights;
  projectId: string;
  onOpenFile: OpenFile;
  onOpenImprovement: (id?: string) => void;
}) {
  const [active, setActive] = useState<Window>("layers");
  const open = insights.proposals.filter((p) => p.state === "OPEN");
  const next = open.find((p) => p.severity === "GAP") ?? open[0];
  const req = insights.requirements;
  const windows: { id: Window; label: string; icon: NovaIconName; count?: number | string; alert?: boolean }[] = [
    { id: "layers", label: "Camadas", icon: "layers", count: insights.layers.reduce((n, l) => n + l.items.length, 0) },
    { id: "api", label: "API", icon: "api", count: insights.endpoints.length },
    { id: "data", label: "Dados", icon: "database", count: insights.entities.length || insights.tables.length },
    { id: "screens", label: "Telas", icon: "screens", count: insights.screens.length },
    { id: "flows", label: "Fluxos", icon: "diagram", count: insights.flows.length },
    { id: "requirements", label: "Requisitos", icon: "backlog", count: req.length ? `${req.filter((r) => r.status === "COVERED").length}/${req.length}` : 0,
      alert: req.some((r) => r.status === "MISSING" || r.status === "PARTIAL") },
    { id: "privacy", label: "Privacidade", icon: "shield", count: insights.privacy.length, alert: insights.privacy.some((p) => p.publicRead) },
    { id: "a11y", label: "Acessibilidade", icon: "eyeOff", count: insights.accessibility.length, alert: insights.accessibility.length > 0 },
    { id: "deps", label: "Dependências", icon: "plug", count: insights.dependencies.backendPackages + insights.dependencies.frontendPackages },
    { id: "ask", label: "Perguntar ao projeto", icon: "copilot" },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Card>
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="font-bold">Saúde do projeto</h2>
            <p className="text-xs text-muted">Versão {insights.version} · {insights.fileCount} arquivos · {insights.testCount} arquivos de teste</p>
          </div>
          <ol className="mt-3 grid grid-cols-[repeat(auto-fit,minmax(8.5rem,1fr))] gap-2">
            {insights.stages.map((s) => (
              <li key={s.id}>
                <button onClick={() => s.proposals.length && onOpenImprovement(s.proposals[0])} disabled={!s.proposals.length}
                  className={`h-full w-full rounded-xl border p-2.5 text-left ${STATUS[s.status].cls} ${s.proposals.length ? "hover:brightness-95" : "cursor-default"}`}>
                  <span className="block text-[11px] font-bold">{STATUS[s.status].label}</span>
                  <span className="block text-sm font-semibold text-text">{s.label}</span>
                  <span className="block text-xs text-muted">{s.summary}</span>
                </button>
              </li>
            ))}
          </ol>
          <div className="mt-4">
            <div className="flex items-center justify-between text-xs font-semibold">
              <span>Caminho até publicar</span>
              <span>{insights.readiness.percent}%</span>
            </div>
            <div className="mt-1 h-2 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-valuenow={insights.readiness.percent} aria-valuemin={0} aria-valuemax={100} aria-label="Caminho até publicar">
              <div className="nova-gradient-bar h-full" style={{ width: `${insights.readiness.percent}%` }} />
            </div>
            <ol className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs">
              {insights.readiness.steps.map((s, i) => (
                <li key={s.id} className={s.done ? "text-success" : "text-muted"}>
                  {i + 1}. {s.label} {s.done ? "✓" : `(${s.pending})`}
                </li>
              ))}
            </ol>
          </div>
        </Card>
        <div className="flex flex-col gap-3 rounded-2xl bg-[#0f1b3d] p-5 text-white">
          <h2 className="font-bold">Próxima melhoria sugerida</h2>
          {next ? (
            <>
              <p className="font-semibold">{next.title}</p>
              <p className="text-sm text-white/80">{next.why}</p>
              <button onClick={() => onOpenImprovement(next.id)} className="mt-auto rounded-lg bg-white px-4 py-2.5 text-sm font-bold text-[#0f1b3d]">
                Revisar e editar a proposta
              </button>
            </>
          ) : (
            <p className="text-sm text-white/80">Nenhuma melhoria pendente nesta versão.</p>
          )}
          {insights.applied.length > 0 && <p className="text-xs text-white/70">{insights.applied.length} melhorias já aplicadas.</p>}
        </div>
      </div>

      <div className="flex flex-wrap items-start gap-4">
        <nav aria-label="Janelas do projeto" className="flex w-full flex-col gap-0.5 rounded-2xl border border-border bg-surface p-2 lg:w-56">
          {windows.map((w) => (
            <button key={w.id} onClick={() => setActive(w.id)} aria-current={active === w.id ? "page" : undefined}
              className={`flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-sm ${active === w.id ? "bg-primary/10 font-semibold text-primary" : "hover:bg-surface-2"}`}>
              <span className="flex items-center gap-2"><NovaIcon name={w.icon} size={16} gradient={active === w.id} />{w.label}</span>
              {w.count !== undefined && (
                <span className={`rounded-full px-2 text-[11px] font-bold ${w.alert ? "bg-danger/15 text-danger" : "text-muted"}`}>{w.count}</span>
              )}
            </button>
          ))}
        </nav>
        <Card className="min-w-0 flex-[999_1_30rem]">
          {active === "layers" && <Layers insights={insights} onOpenFile={onOpenFile} />}
          {active === "api" && <Api insights={insights} onOpenFile={onOpenFile} onOpenImprovement={onOpenImprovement} />}
          {active === "data" && <Data insights={insights} onOpenFile={onOpenFile} />}
          {active === "screens" && <Screens insights={insights} onOpenFile={onOpenFile} />}
          {active === "flows" && <Flows insights={insights} />}
          {active === "requirements" && <Requirements insights={insights} />}
          {active === "privacy" && <Privacy insights={insights} onOpenImprovement={onOpenImprovement} />}
          {active === "a11y" && <Accessibility insights={insights} onOpenFile={onOpenFile} />}
          {active === "deps" && <Deps insights={insights} />}
          {active === "ask" && <Ask projectId={projectId} available={insights.aiAvailable} onOpenFile={onOpenFile} />}
        </Card>
      </div>
    </div>
  );
}

function Title({ children, help }: { children: string; help?: string }) {
  return (
    <div className="mb-3">
      <h2 className="font-bold">{children}</h2>
      {help && <p className="text-sm text-muted">{help}</p>}
    </div>
  );
}

function FileLink({ path, line, onOpenFile, label }: { path: string; line?: number; onOpenFile: OpenFile; label?: string }) {
  return (
    <button onClick={() => onOpenFile(path, line)} className="max-w-full truncate text-left font-mono text-xs text-primary hover:underline" title={path}>
      {label ?? path}{line ? `:${line}` : ""}
    </button>
  );
}

function Layers({ insights, onOpenFile }: { insights: Insights; onOpenFile: OpenFile }) {
  const [role, setRole] = useState(insights.layers[0]?.role ?? "");
  const layer = insights.layers.find((l) => l.role === role) ?? insights.layers[0];
  if (!layer) return <p className="text-sm text-muted">Nenhum código reconhecido.</p>;
  return (
    <>
      <div role="tablist" aria-label="Camada" className="mb-3 flex flex-wrap gap-1 rounded-lg bg-surface-2 p-1">
        {insights.layers.map((l) => (
          <button key={l.role} role="tab" aria-selected={l.role === layer.role} onClick={() => setRole(l.role)}
            className={`rounded-md px-3 py-1.5 text-xs font-semibold ${l.role === layer.role ? "bg-surface shadow-sm" : "text-muted"}`}>
            {l.label} · {l.items.length}
          </button>
        ))}
      </div>
      <p className="mb-3 text-sm text-muted">{layer.help}</p>
      <ul className="flex flex-col gap-1.5">
        {layer.items.map((i) => (
          <li key={i.path + i.name} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2">
            <div className="min-w-0">
              <p className="text-sm font-semibold">{i.name}</p>
              <FileLink path={i.path} onOpenFile={onOpenFile} />
            </div>
            <p className="text-xs text-muted">{i.detail}</p>
          </li>
        ))}
      </ul>
    </>
  );
}

function Api({ insights, onOpenFile, onOpenImprovement }: { insights: Insights; onOpenFile: OpenFile; onOpenImprovement: (id?: string) => void }) {
  const gap = (entity: string | null) => insights.proposals.find((p: Proposal) => p.state === "OPEN" && p.kind === "crud" && entity && p.title.endsWith(entity));
  return (
    <>
      <Title help={`${insights.endpoints.length} rotas. Clique numa rota para abrir o código; "Falta" leva à proposta.`}>API</Title>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[36rem] text-sm">
          <thead>
            <tr className="text-left text-xs text-muted">
              <th className="py-1.5 font-semibold">Recurso</th>
              {OPS.map((o) => <th key={o.id} className="font-semibold">{o.label}</th>)}
              <th className="font-semibold">Outras ações</th>
            </tr>
          </thead>
          <tbody>
            {insights.resources.map((r) => {
              const missing = gap(r.entity);
              return (
                <tr key={r.path} className="border-t border-border align-top">
                  <td className="py-2 pr-2">
                    <span className="font-mono text-xs">{r.path}</span>
                    {r.entity && <span className="block text-xs text-muted">{r.entity}</span>}
                  </td>
                  {OPS.map((o) => {
                    const e = r.operations[o.id];
                    return (
                      <td key={o.id} className="py-2 pr-2 text-xs">
                        {e ? (
                          <button onClick={() => onOpenFile(e.file, e.line)} className="text-left text-success hover:underline">
                            {e.method}<span className="block text-muted">{access(e.access)}</span>
                          </button>
                        ) : r.entity && missing ? (
                          <button onClick={() => onOpenImprovement(missing.id)} className="font-bold text-danger hover:underline">Falta</button>
                        ) : <span className="text-muted">—</span>}
                      </td>
                    );
                  })}
                  <td className="py-2 text-xs">
                    {r.actions.map((a) => (
                      <button key={a.method + a.path} onClick={() => onOpenFile(a.file, a.line)} className="block text-left font-mono hover:underline">
                        {a.method} {a.path.replace(r.path, "") || "/"}
                      </button>
                    ))}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </>
  );
}

function Data({ insights, onOpenFile }: { insights: Insights; onOpenFile: OpenFile }) {
  return (
    <>
      <Title help={`${insights.stack.database ?? "Sem banco"}${insights.stack.flyway ? " · migrações Flyway" : ""}${insights.stack.jpa ? " · JPA" : ""}`}>Dados</Title>
      {insights.entities.length === 0 && <p className="mb-3 text-sm text-muted">Nenhuma entidade no código. A aba Melhorias sugere as que as telas pedem.</p>}
      <div className="grid gap-3 md:grid-cols-2">
        {insights.entities.map((e) => (
          <div key={e.name} className="rounded-xl border border-border p-3">
            <div className="flex items-baseline justify-between gap-2">
              <p className="font-bold">{e.name}</p>
              <p className="font-mono text-xs text-muted">{e.table}</p>
            </div>
            <ul className="mt-2 flex flex-col gap-1 text-xs">
              {e.fields.map((f) => (
                <li key={f.name} className="flex justify-between gap-2">
                  <span>{f.name}{f.required && <span className="text-danger"> *</span>}{f.id && <span className="text-muted"> (id)</span>}</span>
                  <span className="text-muted">{f.enumValues ? f.enumValues.join(" · ") : f.type}</span>
                </li>
              ))}
            </ul>
            {e.relations.length > 0 && <p className="mt-2 text-xs text-muted">Liga com: {e.relations.join(", ")}</p>}
            <div className="mt-2"><FileLink path={e.file} onOpenFile={onOpenFile} /></div>
          </div>
        ))}
      </div>
      {insights.tables.length > 0 && (
        <>
          <h3 className="mb-2 mt-4 text-sm font-bold">Tabelas criadas pelas migrações</h3>
          <ul className="flex flex-col gap-1 text-xs">
            {insights.tables.map((t) => (
              <li key={t.file + t.name}><span className="font-mono font-semibold">{t.name}</span> <span className="text-muted">({t.columns.join(", ")})</span></li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}

function Screens({ insights, onOpenFile }: { insights: Insights; onOpenFile: OpenFile }) {
  return (
    <>
      <Title help="O que cada página consome e se trata lista vazia e falha da API.">Telas</Title>
      <ul className="flex flex-col gap-1.5">
        {insights.screens.map((s) => (
          <li key={s.route} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2">
            <div className="min-w-0">
              <p className="text-sm font-semibold">{s.title ?? s.route} <span className="font-mono text-xs text-muted">{s.route}</span></p>
              <FileLink path={s.file} onOpenFile={onOpenFile} />
            </div>
            <div className="text-right text-xs">
              <p className="text-muted">{s.generatedPlaceholder ? "modelo inicial" : s.apiCalls.length ? s.apiCalls.join(", ") : "sem API"}</p>
              {s.apiCalls.length > 0 && (
                <p className={s.handlesEmpty && s.handlesError ? "text-success" : "text-warning"}>
                  {s.handlesEmpty ? "vazio ✓" : "vazio ✗"} · {s.handlesError ? "erro ✓" : "erro ✗"}
                </p>
              )}
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}

function Flows({ insights }: { insights: Insights }) {
  return (
    <>
      <Title help="O caminho de cada rota pelas camadas, da tela até a tabela.">Fluxos</Title>
      <ul className="flex flex-col gap-2">
        {insights.flows.map((f) => (
          <li key={f.method + f.path} className="rounded-lg border border-border p-3 text-xs">
            <p className="font-mono text-sm font-semibold">{f.method} {f.path} <span className="font-sans text-xs font-normal text-muted">· {access(f.access)}</span></p>
            <ol className="mt-2 flex flex-wrap items-center gap-1.5">
              {[f.screens.length ? `Telas: ${f.screens.join(", ")}` : null, f.controller, ...f.services, f.repository, f.entity, f.table ? `tabela ${f.table}` : null]
                .filter((x): x is string => Boolean(x))
                .map((step, i, all) => (
                  <li key={step + i} className="flex items-center gap-1.5">
                    <span className="rounded-md bg-surface-2 px-2 py-1">{step}</span>
                    {i < all.length - 1 && <span aria-hidden="true" className="text-muted">→</span>}
                  </li>
                ))}
            </ol>
          </li>
        ))}
      </ul>
    </>
  );
}

const REQ: Record<string, { label: string; cls: string }> = {
  COVERED: { label: "Atendido", cls: "bg-success/10 text-success" },
  PARTIAL: { label: "Parcial", cls: "bg-warning/10 text-warning" },
  MISSING: { label: "Ausente", cls: "bg-danger/10 text-danger" },
  UNKNOWN: { label: "Não relacionado", cls: "bg-surface-2 text-muted" },
};
const PRIORITY: Record<string, string> = { ESSENTIAL: "Essencial", IMPORTANT: "Importante", LATER: "Depois" };

function Requirements({ insights }: { insights: Insights }) {
  if (insights.requirements.length === 0) {
    return <><Title>Requisitos</Title><p className="text-sm text-muted">O projeto não tem requisitos registrados. Eles vêm do formulário de criação ou do Copilot.</p></>;
  }
  return (
    <>
      <Title help="Cada requisito do formulário e o que no código o atende.">Requisitos</Title>
      <ul className="flex flex-col gap-2">
        {insights.requirements.map((r, i) => (
          <li key={i} className="rounded-lg border border-border p-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <p className="text-sm font-semibold">{r.text}</p>
              <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${REQ[r.status].cls}`}>{REQ[r.status].label}</span>
            </div>
            <p className="text-xs text-muted">{PRIORITY[r.priority] ?? r.priority}{r.acceptance ? ` · Pronto quando: ${r.acceptance}` : ""}</p>
            {r.evidence.length > 0 && <p className="mt-1 text-xs text-success">Atende: {r.evidence.join(" · ")}</p>}
            {r.missing.length > 0 && <p className="mt-1 text-xs text-danger">Falta: {r.missing.join(" · ")}</p>}
          </li>
        ))}
      </ul>
    </>
  );
}

const KIND: Record<string, string> = { SENSITIVE: "Dado sensível", IDENTIFICATION: "Identificação", CONTACT: "Contato", LOCATION: "Localização", BIRTH: "Nascimento" };

function Privacy({ insights, onOpenImprovement }: { insights: Insights; onOpenImprovement: (id?: string) => void }) {
  const proposal = insights.proposals.find((p) => p.kind === "privacy" && p.state === "OPEN");
  return (
    <>
      <Title help="Dados pessoais encontrados nas entidades, com o que a LGPD pede para cada um.">Privacidade (LGPD)</Title>
      {insights.privacy.length === 0 ? <p className="text-sm text-muted">Nenhum dado pessoal nas entidades.</p> : (
        <ul className="flex flex-col gap-2">
          {insights.privacy.map((p) => (
            <li key={p.entity + p.field} className={`rounded-lg border p-3 ${p.publicRead ? "border-danger/40 bg-danger/5" : "border-border"}`}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-semibold">{p.entity}.{p.field} <span className="text-xs font-normal text-muted">· {KIND[p.kind] ?? p.kind}</span></p>
                <span className="flex flex-wrap gap-1">{p.articles.map((a) => <LgpdTag key={a}>{a}</LgpdTag>)}</span>
              </div>
              <p className="mt-1 text-xs text-muted">{p.advice}</p>
            </li>
          ))}
        </ul>
      )}
      {proposal && <div className="mt-3"><Button onClick={() => onOpenImprovement(proposal.id)}>Ver a proposta: {proposal.title}</Button></div>}
    </>
  );
}

function Accessibility({ insights, onOpenFile }: { insights: Insights; onOpenFile: OpenFile }) {
  return (
    <>
      <Title help="Problemas que impedem pessoas com deficiência de usar as telas.">Acessibilidade</Title>
      {insights.accessibility.length === 0 ? <p className="text-sm text-muted">Nada encontrado nas telas.</p> : (
        <ul className="flex flex-col gap-1.5">
          {insights.accessibility.map((a, i) => (
            <li key={i} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-xs">
              <span>{a.message}</span>
              <FileLink path={a.file} line={a.line} onOpenFile={onOpenFile} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function Deps({ insights }: { insights: Insights }) {
  const d = insights.dependencies;
  return (
    <>
      <Title>Dependências e variáveis</Title>
      <dl className="grid grid-cols-[8rem_minmax(0,1fr)] gap-y-2 text-sm">
        <dt className="text-muted">Backend</dt><dd>{d.backendBuild ?? "—"} · {d.backendPackages} pacotes · {d.backendPlugins} plugins</dd>
        <dt className="text-muted">Frontend</dt><dd>{d.frontendBuild ? `${d.frontendBuild} · ${d.frontendPackages} pacotes` : "—"}</dd>
        <dt className="text-muted">Conectores</dt><dd>{d.connectors.length ? d.connectors.join(", ") : "nenhum"}</dd>
      </dl>
      <h3 className="mb-2 mt-4 text-sm font-bold">Variáveis de ambiente</h3>
      <ul className="flex flex-col gap-1 text-xs">
        {d.env.map((v) => (
          <li key={v.name} className="flex flex-wrap justify-between gap-2 rounded-md border border-border px-2 py-1.5">
            <span className="font-mono font-semibold">{v.name}{v.secret && <span className="font-sans font-normal text-warning"> · segredo</span>}</span>
            <span className={v.documented ? "text-muted" : "font-semibold text-danger"}>{v.documented ? "no .env.example" : "falta no .env.example"}</span>
          </li>
        ))}
      </ul>
    </>
  );
}

function Ask({ projectId, available, onOpenFile }: { projectId: string; available: boolean; onOpenFile: OpenFile }) {
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [answer, setAnswer] = useState<{ answer: string; references: { path: string; line: number; why: string }[] } | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function ask(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const res = await fetch(`/api/backend/projects/${projectId}/ask`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ question }) });
    const body = await res.json().catch(() => ({}));
    setBusy(false);
    if (res.ok) setAnswer(body);
    else setError(body.detail ?? "Não foi possível responder.");
  }

  return (
    <>
      <Title help="Pergunte sobre o código: a IA responde apontando arquivo e linha.">Perguntar ao projeto</Title>
      {!available && <Alert tone="info">Exige a IA do NOVA, que não está configurada neste ambiente.</Alert>}
      <form onSubmit={ask} className="mt-3 flex flex-col gap-2">
        <Textarea aria-label="Pergunta" rows={2} maxLength={1000} value={question} onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ex.: onde é calculado o total do pedido?" disabled={!available} />
        <p className="text-xs text-muted">Os arquivos mais ligados à pergunta são enviados ao provedor de IA para gerar a resposta.</p>
        <div><Button type="submit" disabled={!available || busy || !question.trim()}>{busy ? "Pensando…" : "Perguntar"}</Button></div>
      </form>
      {error && <div className="mt-3"><Alert tone="danger">{error}</Alert></div>}
      {answer && (
        <div className="mt-3 rounded-lg border border-border p-3">
          <p className="whitespace-pre-wrap text-sm">{answer.answer}</p>
          <ul className="mt-2 flex flex-col gap-1">
            {answer.references.map((r, i) => (
              <li key={i} className="text-xs"><FileLink path={r.path} line={r.line || undefined} onOpenFile={onOpenFile} /> — {r.why}</li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}
