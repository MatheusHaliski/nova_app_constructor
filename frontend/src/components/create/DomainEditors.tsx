"use client";

import { useState } from "react";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { FieldsEditor } from "@/components/projects/ImprovementsPanel";
import { Alert, Button, Card, Input } from "@/components/ui/ui";
import type { EntitySpec, ProjectSpec, RequirementSpec, ScreenSpec, ScreenTemplate, ThemeSpec } from "@/lib/catalog-types";

/* Partes do formulário de criação ligadas aos dados do domínio: entidades, requisitos, telas por modelo, tema, esboço e comparação de stacks. */

const TEMPLATES: { id: ScreenTemplate; label: string; needsEntity?: boolean }[] = [
  { id: "LIST", label: "Lista", needsEntity: true },
  { id: "DETAIL", label: "Detalhe", needsEntity: true },
  { id: "FORM", label: "Formulário", needsEntity: true },
  { id: "DASHBOARD", label: "Painel com totais" },
  { id: "LOGIN", label: "Login" },
  { id: "SIGNUP", label: "Cadastro" },
  { id: "LANDING", label: "Apresentação" },
  { id: "SETTINGS", label: "Configurações" },
  { id: "BLANK", label: "Em branco" },
];
const PRIORITIES = [["ESSENTIAL", "Essencial"], ["IMPORTANT", "Importante"], ["LATER", "Depois"]] as const;

export function DomainStep({ spec, onChange, backendId }: { spec: ProjectSpec; onChange: (patch: Partial<ProjectSpec>) => void; backendId: string }) {
  const entities = spec.entities ?? [];
  const requirements = spec.requirements ?? [];
  const setEntity = (i: number, e: EntitySpec) => onChange({ entities: entities.map((x, j) => (j === i ? e : x)) });
  return (
    <>
      <Card>
        <div className="mb-3 flex items-start gap-3">
          <NovaIcon name="database" size={28} gradient />
          <div>
            <h2 className="text-lg font-bold">O que o app guarda</h2>
            <p className="text-sm text-muted">Cada entidade vira tabela, acesso ao banco, API (listar, ver, criar, editar e excluir), testes e, com status, a rota para mudá-lo.</p>
          </div>
        </div>
        {backendId !== "spring-boot" && <div className="mb-3"><Alert tone="info">Hoje o código das entidades é escrito no Spring Boot. Neste backend elas ficam registradas e viram propostas na aba Melhorias.</Alert></div>}
        {entities.length === 0 && <p className="mb-3 text-sm text-muted">Nenhuma entidade ainda. Sem elas o projeto nasce só com a estrutura.</p>}
        <div className="flex flex-col gap-3">
          {entities.map((e, i) => (
            <div key={i} className="rounded-xl border border-border p-3">
              <div className="mb-2 flex items-center gap-2">
                <Input aria-label={`Nome da entidade ${i + 1}`} value={e.name} placeholder="Ex.: Produto" maxLength={40} onChange={(ev) => setEntity(i, { ...e, name: ev.target.value })} className="max-w-xs font-semibold" />
                <button type="button" aria-label={`Remover a entidade ${e.name || i + 1}`} onClick={() => onChange({ entities: entities.filter((_, j) => j !== i) })}
                  className="rounded p-1 text-muted hover:bg-surface-2 hover:text-danger"><NovaIcon name="trash" size={16} /></button>
              </div>
              <FieldsEditor label="Campos" rows={e.fields} onChange={(fields) => setEntity(i, { ...e, fields })}
                help="O id é automático. Status: informe os valores separados por vírgula; o primeiro é o inicial." />
            </div>
          ))}
        </div>
        <div className="mt-3">
          <Button variant="secondary" onClick={() => onChange({ entities: [...entities, { name: "", fields: [{ name: "nome", type: "TEXT", required: true }] }] })} disabled={entities.length >= 15}>
            + Entidade
          </Button>
        </div>
      </Card>
      <Card>
        <h2 className="text-lg font-bold">Requisitos</h2>
        <p className="mb-3 text-sm text-muted">O que o app deve fazer e como saber que está pronto. Depois, o painel do projeto mostra se cada um está atendido no código.</p>
        <div className="flex flex-col gap-2">
          {requirements.map((r, i) => {
            const set = (patch: Partial<RequirementSpec>) => onChange({ requirements: requirements.map((x, j) => (j === i ? { ...x, ...patch } : x)) });
            return (
              <div key={i} className="grid gap-2 rounded-lg border border-border p-2 md:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_9rem_auto]">
                <Input aria-label={`Requisito ${i + 1}`} value={r.text} maxLength={300} placeholder="O sistema deve…" onChange={(e) => set({ text: e.target.value })} />
                <Input aria-label={`Critério de aceite ${i + 1}`} value={r.acceptance ?? ""} maxLength={500} placeholder="Pronto quando…" onChange={(e) => set({ acceptance: e.target.value })} />
                <select aria-label={`Prioridade ${i + 1}`} value={r.priority} onChange={(e) => set({ priority: e.target.value as RequirementSpec["priority"] })}
                  className="rounded-lg border border-border bg-surface px-2 py-2 text-sm">
                  {PRIORITIES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                </select>
                <button type="button" aria-label={`Remover requisito ${i + 1}`} onClick={() => onChange({ requirements: requirements.filter((_, j) => j !== i) })}
                  className="rounded p-1 text-muted hover:bg-surface-2"><NovaIcon name="close" size={14} /></button>
              </div>
            );
          })}
        </div>
        <div className="mt-3">
          <Button variant="secondary" onClick={() => onChange({ requirements: [...requirements, { text: "", acceptance: "", priority: "IMPORTANT" }] })} disabled={requirements.length >= 50}>
            + Requisito
          </Button>
        </div>
      </Card>
    </>
  );
}

export function ScreenSpecsEditor({ specs, entities, auth, onChange }: { specs: ScreenSpec[]; entities: string[]; auth: boolean; onChange: (s: ScreenSpec[]) => void }) {
  const [draft, setDraft] = useState("");
  const slug = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  const taken = new Set(specs.map((s) => slug(s.name)));
  const draftSlug = slug(draft);
  const problem = !draft.trim() ? null : taken.has(draftSlug) ? "Já existe uma tela com essa rota." : ["inicio", "home"].includes(draftSlug) ? "A página inicial (/) já existe em todo projeto." : null;
  function add() {
    const name = draft.trim();
    if (!name || problem || specs.length >= 30) return;
    onChange([...specs, { name, template: entities.length ? "LIST" : "BLANK", entity: entities[0] ?? null, access: "PUBLIC", inMenu: true }]);
    setDraft("");
  }
  const set = (i: number, patch: Partial<ScreenSpec>) => onChange(specs.map((s, j) => (j === i ? { ...s, ...patch } : s)));
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <div className="flex gap-2">
          <Input aria-label="Nome da tela" placeholder="Ex.: Produtos" value={draft} maxLength={60} invalid={Boolean(problem)} onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); add(); } }} />
          <Button variant="secondary" onClick={add} disabled={Boolean(problem)}>Adicionar</Button>
        </div>
        {problem && <p className="text-xs font-medium text-danger">{problem}</p>}
      </div>
      {specs.length === 0 ? <p className="text-sm text-muted">Nenhuma tela ainda. O frontend é gerado só com a página inicial.</p> : (
        <ol className="flex flex-col gap-2">
          {specs.map((s, i) => {
            const template = TEMPLATES.find((t) => t.id === s.template) ?? TEMPLATES[TEMPLATES.length - 1];
            return (
              <li key={s.name} className="flex flex-col gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                <div className="flex items-center gap-3">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-surface-2 text-xs font-bold">{i + 1}</span>
                  <span className="flex-1 font-semibold">{s.name} <span className="font-mono text-xs font-normal text-muted">/{slug(s.name)}{s.template === "DETAIL" ? "/[id]" : ""}</span></span>
                  <button aria-label={`Remover ${s.name}`} onClick={() => onChange(specs.filter((_, j) => j !== i))} className="rounded p-1 text-muted hover:bg-surface-2 hover:text-danger">
                    <NovaIcon name="trash" size={16} />
                  </button>
                </div>
                <div className="flex flex-wrap items-center gap-2 pl-9">
                  <select aria-label={`Modelo da tela ${s.name}`} value={s.template} onChange={(e) => set(i, { template: e.target.value as ScreenTemplate })}
                    className="rounded-lg border border-border bg-surface px-2 py-1.5 text-xs">
                    {TEMPLATES.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
                  </select>
                  {template.needsEntity && (
                    <select aria-label={`Entidade da tela ${s.name}`} value={s.entity ?? ""} onChange={(e) => set(i, { entity: e.target.value || null })}
                      className="rounded-lg border border-border bg-surface px-2 py-1.5 text-xs">
                      <option value="">Escolha a entidade…</option>
                      {entities.filter(Boolean).map((e) => <option key={e} value={e}>{e}</option>)}
                    </select>
                  )}
                  {auth && (
                    <select aria-label={`Acesso da tela ${s.name}`} value={s.access ?? "PUBLIC"} onChange={(e) => set(i, { access: e.target.value as "PUBLIC" | "AUTH" })}
                      className="rounded-lg border border-border bg-surface px-2 py-1.5 text-xs">
                      <option value="PUBLIC">Pública</option>
                      <option value="AUTH">Exige login</option>
                    </select>
                  )}
                  <label className="flex items-center gap-1 text-xs"><input type="checkbox" checked={s.inMenu ?? true} onChange={(e) => set(i, { inMenu: e.target.checked })} /> no menu</label>
                  {template.needsEntity && !s.entity && <span className="text-xs text-warning">sem entidade, a tela sai em branco</span>}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}

export function ThemeEditor({ theme, onChange }: { theme: ThemeSpec; onChange: (t: ThemeSpec) => void }) {
  const select = (id: keyof ThemeSpec, label: string, options: [string, string][]) => (
    <label className="flex flex-col gap-1 text-sm font-semibold">{label}
      <select value={theme[id]} onChange={(e) => onChange({ ...theme, [id]: e.target.value })} className="rounded-lg border border-border bg-surface px-2 py-2 text-sm font-normal">
        {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    </label>
  );
  const radius = { NONE: 0, SM: 4, MD: 8, LG: 16 }[theme.radius] ?? 8;
  return (
    <Card>
      <h2 className="text-lg font-bold">Tema</h2>
      <p className="mb-3 text-sm text-muted">Vale para todas as telas. No código, vira variáveis CSS que você muda num lugar só.</p>
      <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_16rem]">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm font-semibold">Cor principal
            <span className="flex items-center gap-2">
              <input type="color" aria-label="Cor principal" value={theme.primary} onChange={(e) => onChange({ ...theme, primary: e.target.value.toUpperCase() })} className="h-10 w-14 rounded border border-border" />
              <span className="font-mono text-xs">{theme.primary}</span>
            </span>
          </label>
          {select("font", "Fonte", [["system", "Do sistema"], ["Inter", "Inter"], ["Roboto", "Roboto"], ["Montserrat", "Montserrat"], ["Lora", "Lora (serifada)"]])}
          {select("radius", "Cantos", [["NONE", "Retos"], ["SM", "Pouco arredondados"], ["MD", "Arredondados"], ["LG", "Bem arredondados"]])}
          {select("density", "Espaçamento", [["COMFORTABLE", "Confortável"], ["COMPACT", "Compacto"]])}
        </div>
        <div aria-hidden="true" className="flex flex-col gap-2 rounded-xl border border-border bg-[#f6f8fb] p-3" style={{ fontFamily: theme.font === "system" ? undefined : theme.font }}>
          <div className="rounded px-2 py-1 text-xs font-bold" style={{ borderBottom: `3px solid ${theme.primary}`, color: theme.primary }}>Meu app</div>
          <div className="bg-white p-2 text-xs" style={{ borderRadius: radius, padding: theme.density === "COMPACT" ? 8 : 14 }}>Cartão de exemplo</div>
          <span className="self-start px-3 py-1.5 text-xs font-semibold text-white" style={{ background: theme.primary, borderRadius: radius }}>Botão</span>
        </div>
      </div>
    </Card>
  );
}

type Comparison = { backend: string; name: string; language: string | null; buildTool: string | null; backendFiles: number; folders: string[]; dependencies: number;
  plugins: number; build: string | null; run: string | null; entitiesAsCode: boolean; requires: string | null; warnings: string[]; error: string | null };

export function StackComparison({ spec }: { spec: ProjectSpec }) {
  const [rows, setRows] = useState<Comparison[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function compare() {
    setBusy(true);
    setError(null);
    const res = await fetch("/api/backend/generator/compare", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ...spec, name: spec.name || "Comparação" }) });
    const body = await res.json().catch(() => ({}));
    setBusy(false);
    if (res.ok) setRows(body);
    else setError(body.detail ?? "Não foi possível comparar.");
  }
  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="font-bold">Comparar stacks</h2>
          <p className="text-sm text-muted">A mesma ideia gerada em cada backend, sem salvar, para escolher com dados.</p>
        </div>
        <Button variant="secondary" icon="layers" onClick={compare} disabled={busy}>{busy ? "Comparando…" : "Comparar"}</Button>
      </div>
      {error && <div className="mt-3"><Alert tone="danger">{error}</Alert></div>}
      {rows && (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[36rem] text-sm">
            <thead><tr className="text-left text-xs text-muted"><th className="py-1.5 font-semibold">Backend</th><th className="font-semibold">Exige</th><th className="font-semibold">Arquivos</th><th className="font-semibold">Dependências</th><th className="font-semibold">Pastas</th><th className="font-semibold">Entidades em código</th><th className="font-semibold">Compilar</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.backend} className="border-t border-border align-top">
                  <td className="py-2 font-semibold">{r.name}<span className="block text-xs font-normal text-muted">{r.language}</span></td>
                  {r.error ? <td colSpan={6} className="py-2 text-xs text-danger">{r.error}</td> : (
                    <>
                      <td className="py-2 text-xs">{r.requires}</td>
                      <td className="py-2">{r.backendFiles}</td>
                      <td className="py-2">{r.dependencies}{r.plugins ? ` + ${r.plugins} plugins` : ""}</td>
                      <td className="py-2 font-mono text-xs">{r.folders.join(" ")}</td>
                      <td className="py-2 text-xs">{r.entitiesAsCode ? "Sim" : "Viram propostas"}</td>
                      <td className="py-2 font-mono text-xs">{r.build}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

type Sketch = { screens: ScreenSpec[]; entities: EntitySpec[]; elements: string[]; notes: string | null };

export function SketchUpload({ onResult }: { onResult: (s: Sketch) => void }) {
  const [busy, setBusy] = useState(false);
  const [hint, setHint] = useState("");
  const [message, setMessage] = useState<{ tone: "info" | "danger" | "success"; text: string } | null>(null);
  async function send(file: File) {
    setBusy(true);
    setMessage(null);
    const form = new FormData();
    form.append("image", file);
    if (hint) form.append("hint", hint);
    const res = await fetch("/api/backend/copilot/sketch", { method: "POST", body: form });
    const body = await res.json().catch(() => ({}));
    setBusy(false);
    if (res.ok) {
      onResult(body);
      setMessage({ tone: "success", text: `Esboço lido: ${body.screens.length} telas e ${body.entities.length} entidades entraram no formulário.${body.notes ? " " + body.notes : ""}` });
    } else setMessage({ tone: res.status === 409 ? "info" : "danger", text: body.detail ?? "Não foi possível ler o esboço." });
  }
  return (
    <div className="mt-4 rounded-xl border border-dashed border-border p-3">
      <p className="text-sm font-semibold">Ou comece de um esboço</p>
      <p className="text-xs text-muted">Foto de um desenho de tela ou print de outro app (PNG, JPEG, WebP, até 5 MB). A imagem é enviada ao provedor de IA.</p>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <Input aria-label="O que manter do esboço" value={hint} maxLength={300} placeholder="O que manter (ex.: layout e cores)" onChange={(e) => setHint(e.target.value)} className="min-w-0 flex-1" />
        <label className={`inline-flex cursor-pointer items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-2 ${busy ? "opacity-60" : ""}`}>
          <NovaIcon name="screens" size={16} /> {busy ? "Lendo…" : "Enviar imagem"}
          <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="sr-only" disabled={busy}
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void send(f); e.target.value = ""; }} />
        </label>
      </div>
      {message && <div className="mt-2"><Alert tone={message.tone}>{message.text}</Alert></div>}
    </div>
  );
}
