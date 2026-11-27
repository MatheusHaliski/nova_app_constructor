"use client";

import { useCallback, useMemo, useState } from "react";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Alert, Button, Card, Input, Textarea, Toggle } from "@/components/ui/ui";
import type { Category, ChangeSet, EnvRow, FieldRow, Insights, OpenFile, Param, Preview, PreviewBundle, Proposal } from "./insights-types";
import { PreviewPanel } from "./PreviewPanel";

/*
 * Aba Melhorias (Continuous Improvement): propostas por parte do projeto. Cada uma pode ser ajustada antes de aplicar;
 * "O que muda" mostra o código, "Prévia das telas" mostra o resultado, e aplicar grava uma nova versão.
 */

const CATEGORY: Record<Category, string> = {
  BUSINESS: "Regras do negócio", PRIVACY: "Privacidade", SECURITY: "Segurança", TESTS: "Testes", SCREENS: "Telas", CONFIG: "Configuração", REVIEW: "Revisões da rede",
};
const FIELD_TYPES = [
  ["TEXT", "Texto"], ["LONG_TEXT", "Texto longo"], ["INTEGER", "Inteiro"], ["DECIMAL", "Decimal"], ["BOOLEAN", "Sim/não"], ["DATE", "Data"],
  ["DATETIME", "Data e hora"], ["STATUS", "Status"],
] as const;

type Filter = "OPEN" | "DISMISSED" | "APPLIED";

async function call<T>(url: string, body?: unknown, method = "POST"): Promise<{ ok: true; data: T } | { ok: false; detail: string }> {
  const res = await fetch(url, { method, headers: body === undefined ? undefined : { "Content-Type": "application/json" }, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  return res.ok ? { ok: true, data } : { ok: false, detail: data?.detail ?? "Não foi possível concluir." };
}

export function ImprovementsPanel({ insights, projectId, readOnly, initialId, onApplied, onOpenFile }: {
  insights: Insights;
  projectId: string;
  readOnly: boolean;
  initialId?: string;
  onApplied: (message: string) => Promise<void>;
  onOpenFile: OpenFile;
}) {
  const [filter, setFilter] = useState<Filter>("OPEN");
  const visible = insights.proposals.filter((p) => p.state === filter);
  const [selectedId, setSelectedId] = useState<string | undefined>(initialId ?? insights.proposals.find((p) => p.state === "OPEN")?.id);
  const selected = insights.proposals.find((p) => p.id === selectedId);
  const groups = useMemo(() => {
    const g = new Map<Category, Proposal[]>();
    visible.forEach((p) => g.set(p.category, [...(g.get(p.category) ?? []), p]));
    return [...g.entries()];
  }, [visible]);

  return (
    <div className="flex flex-col gap-4">
      <ol aria-label="Etapas da análise" className="grid grid-cols-[repeat(auto-fit,minmax(9rem,1fr))] gap-2 rounded-2xl border border-border bg-surface p-3">
        {insights.stages.map((s, i) => (
          <li key={s.id} className="px-1">
            <p className={`text-[11px] font-bold ${s.status === "PASS" ? "text-success" : s.status === "FAIL" ? "text-danger" : s.status === "WARN" ? "text-warning" : "text-muted"}`}>
              {i + 1} · {s.status === "PASS" ? "Passou" : s.status === "NOT_RUN" ? "Não executada" : s.status === "NOT_APPLICABLE" ? "—" : s.summary}
            </p>
            <p className="text-sm font-semibold">{s.label}</p>
          </li>
        ))}
      </ol>

      <div className="flex flex-wrap items-start gap-4">
        <section aria-label="Propostas" className="flex w-full flex-col gap-3 rounded-2xl border border-border bg-surface p-3 lg:w-[22rem]">
          <div role="tablist" aria-label="Situação" className="flex gap-1 rounded-lg bg-surface-2 p-1">
            {([["OPEN", "Abertas"], ["DISMISSED", "Dispensadas"], ["APPLIED", "Aplicadas"]] as const).map(([id, label]) => (
              <button key={id} role="tab" aria-selected={filter === id} onClick={() => setFilter(id)}
                className={`flex-1 rounded-md px-2 py-1.5 text-xs font-semibold ${filter === id ? "bg-surface shadow-sm" : "text-muted"}`}>
                {label} · {id === "APPLIED" ? insights.applied.length : insights.proposals.filter((p) => p.state === id).length}
              </button>
            ))}
          </div>
          {filter === "APPLIED" ? (
            insights.applied.length === 0 ? <p className="px-1 text-sm text-muted">Nenhuma melhoria aplicada ainda.</p> : (
              <ul className="flex flex-col gap-1.5">
                {insights.applied.map((a) => (
                  <li key={a.proposalId + a.version} className="rounded-lg border border-border px-3 py-2 text-sm">
                    <p className="font-semibold">{a.title}</p>
                    <p className="text-xs text-muted">Versão {a.version}{a.usedAi ? " · com IA" : ""} · {new Date(a.appliedAt).toLocaleString("pt-BR")}</p>
                  </li>
                ))}
              </ul>
            )
          ) : visible.length === 0 ? (
            <p className="px-1 text-sm text-muted">{filter === "OPEN" ? "Nenhuma melhoria pendente nesta versão." : "Nenhuma proposta dispensada."}</p>
          ) : (
            groups.map(([category, list]) => (
              <div key={category}>
                <p className="mb-1 px-1 text-[11px] font-bold uppercase tracking-wide text-muted">{CATEGORY[category]}</p>
                <ul className="flex flex-col gap-1.5">
                  {list.map((p) => (
                    <li key={p.id}>
                      <button onClick={() => setSelectedId(p.id)} aria-pressed={p.id === selectedId}
                        className={`w-full rounded-xl border p-3 text-left ${p.id === selectedId ? "border-2 border-primary bg-primary/5" : "border-border hover:bg-surface-2"}`}>
                        <span className="flex justify-between gap-2 text-[11px] font-bold">
                          <span className="text-muted">{p.part}</span>
                          <span className={p.severity === "GAP" ? "text-danger" : "text-warning"}>{p.severity === "GAP" ? "LACUNA" : "SUGESTÃO"}</span>
                        </span>
                        <span className="mt-1 block text-sm font-semibold">{p.title}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ))
          )}
        </section>

        <div className="min-w-0 flex-[999_1_32rem]">
          {selected ? (
            <ProposalEditor key={selected.id + insights.version} proposal={selected} projectId={projectId} version={insights.version} readOnly={readOnly}
              onApplied={onApplied} onOpenFile={onOpenFile} />
          ) : (
            <Card className="py-12 text-center text-sm text-muted">Escolha uma proposta para ver os detalhes.</Card>
          )}
        </div>
      </div>
    </div>
  );
}

function ProposalEditor({ proposal: p, projectId, version, readOnly, onApplied, onOpenFile }: {
  proposal: Proposal;
  projectId: string;
  version: number;
  readOnly: boolean;
  onApplied: (message: string) => Promise<void>;
  onOpenFile: OpenFile;
}) {
  const base = `/api/backend/projects/${projectId}/improvements/${p.id}`;
  const [values, setValues] = useState<Record<string, unknown>>(() => Object.fromEntries(p.params.map((x) => [x.name, x.value])));
  const [useAi, setUseAi] = useState(false);
  const [note, setNote] = useState("");
  const [preview, setPreview] = useState<ChangeSet | null>(null);
  const [nextVersion, setNextVersion] = useState(version + 1);
  const [showScreens, setShowScreens] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const body = useCallback(() => ({ baseVersion: version, params: values, useAi, note: note || null,
    files: useAi && preview ? preview.changes.map((c) => ({ path: c.path, content: c.after })) : null }), [version, values, useAi, note, preview]);
  const canRun = p.automatic || (useAi && p.aiAvailable);

  function set(name: string, value: unknown) {
    setValues((v) => ({ ...v, [name]: value }));
    setPreview(null);
    setShowScreens(false);
  }

  async function seeChanges() {
    setBusy(true);
    setError(null);
    const r = await call<Preview>(`${base}/preview`, { ...body(), files: null });
    setBusy(false);
    if (r.ok) {
      setPreview(r.data.changeSet);
      setNextVersion(r.data.nextVersion);
    } else setError(r.detail);
  }

  async function apply() {
    setBusy(true);
    setError(null);
    const r = await call<{ version: number; notes: string[] }>(`${base}/apply`, body());
    setBusy(false);
    if (r.ok) await onApplied(`Melhoria aplicada: versão ${r.data.version} criada.${r.data.notes.length ? " " + r.data.notes.join(" ") : ""}`);
    else setError(r.detail);
  }

  async function dismiss(on: boolean) {
    const r = await call(`${base}/dismissal`, undefined, on ? "PUT" : "DELETE");
    if (r.ok) await onApplied(on ? "Proposta dispensada." : "Proposta reaberta.");
    else setError(r.detail);
  }

  const loadScreens = useCallback(async (): Promise<PreviewBundle | { error: string }> => {
    const r = await call<PreviewBundle>(`${base}/preview-bundle`, { ...body(), files: null });
    return r.ok ? r.data : { error: r.detail };
  }, [base, body]);

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <p className={`text-xs font-bold ${p.severity === "GAP" ? "text-danger" : "text-warning"}`}>{p.part} · {p.severity === "GAP" ? "LACUNA" : "SUGESTÃO"}</p>
        <h2 className="mt-1 text-xl font-bold">{p.title}</h2>
        <p className="mt-2 text-sm">{p.why}</p>
        {p.where.length > 0 && (
          <p className="mt-2 flex flex-wrap gap-x-3 text-xs text-muted">Encontrado em:
            {p.where.map((w) => <button key={w} onClick={() => onOpenFile(w)} className="font-mono text-primary hover:underline">{w}</button>)}
          </p>
        )}
        {p.howTo && <div className="mt-3"><Alert tone="info">{p.howTo}</Alert></div>}
      </Card>

      {p.state === "OPEN" && !readOnly && (
        <div className="flex flex-wrap items-start gap-4">
          <Card className="flex min-w-0 flex-[1_1_20rem] flex-col gap-4">
            <h3 className="font-bold">Ajuste a proposta antes de aplicar</h3>
            {p.params.filter((x) => x.type !== "HIDDEN").length === 0 && <p className="text-sm text-muted">Esta melhoria não tem opções.</p>}
            {p.params.filter((x) => x.type !== "HIDDEN").map((x) => <ParamInput key={x.name} param={x} value={values[x.name]} onChange={(v) => set(x.name, v)} />)}
            <div className="border-t border-border pt-3">
              <Switch id={`ai-${p.id}`} label="Aplicar com IA" checked={useAi} onChange={(v) => { setUseAi(v); setPreview(null); }} disabled={!p.aiAvailable}
                description={p.aiAvailable ? "A IA parte da proposta e da sua instrução; os arquivos envolvidos são enviados ao provedor." : "A IA do NOVA não está configurada neste ambiente."} />
              {useAi && (
                <Textarea aria-label="Instrução extra para a IA" rows={2} maxLength={1000} value={note} onChange={(e) => { setNote(e.target.value); setPreview(null); }}
                  placeholder="Ex.: avisar o cliente por e-mail quando o pedido for enviado" className="mt-2" />
              )}
            </div>
          </Card>

          <Card className="flex min-w-0 flex-[1_1_20rem] flex-col gap-3">
            <h3 className="font-bold">O que muda no projeto</h3>
            {error && <Alert tone="danger">{error}</Alert>}
            {!preview ? (
              <p className="text-sm text-muted">{canRun ? "Veja os arquivos que serão criados ou alterados antes de aplicar." : "Sem aplicação automática: siga o passo a passo acima."}</p>
            ) : (
              <>
                <p className="text-sm">{preview.summary}</p>
                <ul className="flex flex-col gap-1.5">
                  {preview.changes.map((c) => (
                    <li key={c.path}>
                      <details className="rounded-lg border border-border">
                        <summary className="flex cursor-pointer items-baseline gap-2 px-3 py-2 text-xs">
                          <span className={`rounded px-1.5 py-0.5 font-bold ${c.action === "NEW" ? "bg-success/10 text-success" : "bg-warning/10 text-warning"}`}>{c.action === "NEW" ? "NOVO" : "EDITA"}</span>
                          <span className="min-w-0 break-all font-mono">{c.path}</span>
                        </summary>
                        {c.why && <p className="px-3 text-xs text-muted">{c.why}</p>}
                        <pre className="max-h-80 overflow-auto px-3 py-2 font-mono text-[11px] leading-relaxed">{c.after}</pre>
                      </details>
                    </li>
                  ))}
                </ul>
                {preview.notes.map((n) => <p key={n} className="text-xs text-muted">{n}</p>)}
              </>
            )}
            <div className="mt-auto flex flex-wrap gap-2">
              <Button variant="secondary" icon="tree" onClick={seeChanges} disabled={!canRun || busy}>Ver o código</Button>
              <Button variant="secondary" icon="eyeOff" onClick={() => setShowScreens((s) => !s)} disabled={!canRun || busy || useAi}>
                {showScreens ? "Esconder a prévia" : "Prévia das telas"}
              </Button>
              <Button icon="check" onClick={apply} disabled={!canRun || busy || (useAi && !preview)}>{busy ? "Aplicando…" : `Aplicar como versão ${nextVersion}`}</Button>
              <Button variant="ghost" onClick={() => dismiss(true)} disabled={busy}>Dispensar</Button>
            </div>
            <p className="text-xs text-muted">Aplicar cria a versão {nextVersion}; a versão {version} continua no histórico e pode ser restaurada.</p>
          </Card>
        </div>
      )}
      {p.state === "DISMISSED" && !readOnly && <div><Button variant="secondary" onClick={() => dismiss(false)}>Reabrir proposta</Button></div>}
      {showScreens && <PreviewPanel load={loadScreens} editable={false} />}
    </div>
  );
}

function ParamInput({ param: x, value, onChange }: { param: Param; value: unknown; onChange: (v: unknown) => void }) {
  const id = `param-${x.name}`;
  const label = <label htmlFor={id} className="text-sm font-semibold">{x.label}</label>;
  const help = x.help && <p className="text-xs text-muted">{x.help}</p>;
  switch (x.type) {
    case "BOOLEAN":
      return <Switch id={id} label={x.label} checked={Boolean(value)} onChange={onChange} description={x.help ?? undefined} />;
    case "SELECT":
      return (
        <div className="flex flex-col gap-1.5">{label}
          <select id={id} value={String(value ?? "")} onChange={(e) => onChange(e.target.value)} className="rounded-lg border border-border bg-surface px-3 py-2 text-sm">
            {x.options?.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>{help}
        </div>
      );
    case "MULTI": {
      const list = Array.isArray(value) ? (value as string[]) : [];
      return (
        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-sm font-semibold">{x.label}</legend>
          <div className="flex flex-wrap gap-1.5">
            {x.options?.map((o) => {
              const on = list.includes(o.value);
              return (
                <button key={o.value} type="button" aria-pressed={on} onClick={() => onChange(on ? list.filter((v) => v !== o.value) : [...list, o.value])}
                  className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${on ? "border-primary bg-primary/10 text-primary" : "border-border text-muted line-through"}`}>{o.label}</button>
              );
            })}
          </div>{help}
        </fieldset>
      );
    }
    case "LIST": {
      const list = Array.isArray(value) ? (value as string[]) : [];
      return (
        <div className="flex flex-col gap-1.5">{label}
          <Input id={id} value={list.join(", ")} onChange={(e) => onChange(e.target.value.split(",").map((s) => s.trim().toUpperCase()).filter(Boolean))} />{help}
        </div>
      );
    }
    case "FIELDS":
      return <FieldsEditor label={x.label} help={x.help} rows={(value as FieldRow[]) ?? []} onChange={onChange} />;
    case "ENV": {
      const rows = (value as EnvRow[]) ?? [];
      return (
        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-sm font-semibold">{x.label}</legend>
          {rows.map((r, i) => (
            <div key={r.name} className="flex items-center gap-2">
              <input type="checkbox" aria-label={`Incluir ${r.name}`} checked={r.include} onChange={(e) => onChange(rows.map((x2, j) => (j === i ? { ...x2, include: e.target.checked } : x2)))} />
              <span className="w-44 truncate font-mono text-xs">{r.name}</span>
              <Input aria-label={`Valor de exemplo de ${r.name}`} value={r.value} placeholder="valor de exemplo (opcional)"
                onChange={(e) => onChange(rows.map((x2, j) => (j === i ? { ...x2, value: e.target.value } : x2)))} />
            </div>
          ))}{help}
        </fieldset>
      );
    }
    default:
      return (
        <div className="flex flex-col gap-1.5">{label}
          <Input id={id} value={String(value ?? "")} onChange={(e) => onChange(e.target.value)} className={x.name === "path" || x.name === "route" ? "font-mono" : ""} />{help}
        </div>
      );
  }
}

export function FieldsEditor({ label, help, rows, onChange }: { label: string; help?: string | null; rows: FieldRow[]; onChange: (rows: FieldRow[]) => void }) {
  const update = (i: number, patch: Partial<FieldRow>) => onChange(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-semibold">{label}</legend>
      {rows.map((r, i) => (
        <div key={i} className="flex flex-col gap-1.5 rounded-lg border border-border p-2">
          <div className="flex flex-wrap items-center gap-2">
            <Input aria-label={`Nome do campo ${i + 1}`} value={r.name} onChange={(e) => update(i, { name: e.target.value })} className="min-w-0 flex-1 font-mono" />
            <select aria-label={`Tipo do campo ${i + 1}`} value={r.type} onChange={(e) => update(i, { type: e.target.value, values: e.target.value === "STATUS" ? r.values ?? ["CRIADO", "CONCLUIDO"] : undefined })}
              className="rounded-lg border border-border bg-surface px-2 py-2 text-sm">
              {FIELD_TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
            <label className="flex items-center gap-1 text-xs"><input type="checkbox" checked={Boolean(r.required) || r.type === "STATUS"} disabled={r.type === "STATUS"}
              onChange={(e) => update(i, { required: e.target.checked })} /> obrigatório</label>
            <button type="button" aria-label={`Remover campo ${r.name || i + 1}`} onClick={() => onChange(rows.filter((_, j) => j !== i))} className="rounded p-1 text-muted hover:bg-surface-2">
              <NovaIcon name="close" size={14} />
            </button>
          </div>
          {r.type === "STATUS" && (
            <Input aria-label={`Valores de ${r.name}`} value={(r.values ?? []).join(", ")} placeholder="CRIADO, PAGO, ENVIADO"
              onChange={(e) => update(i, { values: e.target.value.split(",").map((s) => s.trim().toUpperCase()).filter(Boolean) })} className="font-mono text-xs" />
          )}
        </div>
      ))}
      <div><Button type="button" variant="ghost" onClick={() => onChange([...rows, { name: "", type: "TEXT", required: false }])}>+ Campo</Button></div>
      {help && <p className="text-xs text-muted">{help}</p>}
    </fieldset>
  );
}

function Switch({ id, label, checked, onChange, disabled, description }: {
  id: string; label: string; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; description?: string;
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div>
        <p className="text-sm font-semibold">{label}</p>
        {description && <p className="text-xs text-muted">{description}</p>}
      </div>
      <Toggle id={id} label={label} checked={checked} onChange={onChange} disabled={disabled} />
    </div>
  );
}
