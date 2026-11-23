"use client";

import { useRouter } from "next/navigation";
import { useMemo, useState, type ReactNode } from "react";
import { NovaIcon, type NovaIconName } from "@/components/icons/NovaIcon";
import { CatalogIcon } from "@/components/tech/CatalogIcon";
import { Alert, Button, Card, Field, Input, Textarea, Toggle } from "@/components/ui/ui";
import type { Catalog, Pkg, Plan, ProjectSpec, Proposal, Stack } from "@/lib/catalog-types";
import type { IconData } from "@/lib/techs";

/*
 * Formulário de criação de app (Objetivo 1). Dois modos:
 * - Copilot total: a pessoa descreve a ideia e a IA propõe tudo; ela só revisa.
 * - Assistido: a pessoa preenche e pede ajuda da IA campo a campo.
 * Nos dois modos as mesmas opções vêm do catálogo: versão da linguagem e do framework, ferramenta de build,
 * pacotes, plugins, banco, conectores e IA externa. A revisão mostra o plano (dependências, variáveis,
 * como compilar e arquivos) antes de gerar.
 */

type Mode = "COPILOT" | "ASSISTED";
type Problem = { detail?: string; errors?: Record<string, string> };

const STEPS = ["Modo", "Projeto", "Tecnologias", "Pacotes e conectores", "Telas", "Revisão"] as const;

async function call<T>(path: string, body: unknown): Promise<{ ok: true; data: T } | { ok: false; problem: Problem }> {
  const res = await fetch(`/api/backend/${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  if (res.ok) return { ok: true, data: (await res.json()) as T };
  return { ok: false, problem: (await res.json().catch(() => ({}))) as Problem };
}

function defaultPackages(stack: Stack | undefined): string[] {
  return stack ? stack.packages.filter((p) => p.default && !p.required && !p.feature).map((p) => p.id) : [];
}

function initialSpec(catalog: Catalog, mode: Mode): ProjectSpec {
  const backend = catalog.backends.find((b) => b.status === "available")!;
  const frontend = catalog.frontends.find((f) => f.status === "available")!;
  const database = catalog.databases.find((d) => d.status === "available")!;
  const language = catalog.languages.find((l) => l.id === backend.languages?.[0])!;
  return {
    name: "",
    description: "",
    objective: "",
    type: "WEB_APP",
    mode,
    backend: {
      id: backend.id,
      version: backend.defaultVersion,
      language: language.id,
      languageVersion: language.defaultVersion,
      buildTool: backend.defaultBuildTool,
      packages: defaultPackages(backend),
    },
    frontend: { id: frontend.id, version: frontend.defaultVersion, packages: defaultPackages(frontend) },
    database: { id: database.id, version: database.defaultVersion },
    connectors: [],
    ai: null,
    features: catalog.features.filter((f) => f.default).map((f) => f.id),
    screens: [],
  };
}

export function CreateWizard({ catalog, icons, initialMode }: { catalog: Catalog; icons: Record<string, IconData>; initialMode: Mode | null }) {
  const router = useRouter();
  const [step, setStep] = useState(initialMode ? 1 : 0);
  const [spec, setSpec] = useState<ProjectSpec>(() => initialSpec(catalog, initialMode ?? "ASSISTED"));
  const [idea, setIdea] = useState("");
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const [plan, setPlan] = useState<Plan | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const backend = catalog.backends.find((b) => b.id === spec.backend.id)!;
  const frontend = spec.frontend ? catalog.frontends.find((f) => f.id === spec.frontend!.id) : undefined;
  const database = spec.database ? catalog.databases.find((d) => d.id === spec.database!.id) : undefined;
  const ai = spec.ai ? catalog.aiProviders.find((a) => a.id === spec.ai!.provider) : undefined;
  const language = catalog.languages.find((l) => l.id === spec.backend.language);
  const buildTool = backend.buildTools.find((b) => b.id === spec.backend.buildTool);

  const update = (patch: Partial<ProjectSpec>) => {
    setSpec((s) => ({ ...s, ...patch }));
    setPlan(null);
  };

  function chooseBackend(id: string) {
    const next = catalog.backends.find((b) => b.id === id)!;
    const lang = catalog.languages.find((l) => l.id === next.languages?.[0])!;
    update({
      backend: { id, version: next.defaultVersion, language: lang.id, languageVersion: lang.defaultVersion, buildTool: next.defaultBuildTool, packages: defaultPackages(next) },
    });
  }

  function chooseFrontend(id: string | null) {
    if (!id) return update({ frontend: null, screens: [] });
    const next = catalog.frontends.find((f) => f.id === id)!;
    update({ frontend: { id, version: next.defaultVersion, packages: defaultPackages(next) } });
  }

  async function runCopilot() {
    setBusy("copilot");
    setError(null);
    const result = await call<Proposal>("copilot/propose", { idea, type: spec.type });
    setBusy(null);
    if (!result.ok) return setError(result.problem.detail ?? "Não foi possível gerar a proposta.");
    setProposal(result.data);
    setSpec({ ...result.data.spec, mode: "COPILOT" });
    setPlan(null);
  }

  async function assist(field: "description" | "objective" | "screens") {
    setBusy(field);
    setError(null);
    const current = field === "screens" ? spec.screens.join("\n") : (spec[field] ?? "");
    const result = await call<{ suggestion: string; notice: string | null }>("copilot/assist", {
      field,
      idea: idea || spec.objective || spec.description || spec.name,
      name: spec.name,
      current,
    });
    setBusy(null);
    if (!result.ok) return setError(result.problem.detail ?? "Não foi possível sugerir agora.");
    if (field === "screens") {
      update({ screens: result.data.suggestion.split("\n").map((s) => s.replace(/^[-*\d.\s]+/, "").trim()).filter(Boolean).slice(0, 30) });
    } else {
      update({ [field]: result.data.suggestion.slice(0, field === "description" ? 500 : 4000) });
    }
    if (result.data.notice) setError(result.data.notice);
  }

  async function review() {
    setBusy("plan");
    setError(null);
    const result = await call<Plan>("generator/plan", spec);
    setBusy(null);
    if (!result.ok) {
      setPlan(null);
      return setError(result.problem.detail ?? "Revise as escolhas.");
    }
    setPlan(result.data);
  }

  async function generate() {
    setBusy("generate");
    setError(null);
    const result = await call<{ id: string }>("generator/projects", spec);
    if (!result.ok) {
      setBusy(null);
      return setError(result.problem.detail ?? "Não foi possível gerar o projeto.");
    }
    router.push(`/projetos/${result.data.id}`);
  }

  function goTo(next: number) {
    setError(null);
    if (next >= 2 && !spec.name.trim()) {
      setStep(1);
      return setError("Dê um nome ao projeto para continuar.");
    }
    const skipScreens = !spec.frontend && next === 4;
    const target = skipScreens ? (next > step ? 5 : 3) : next;
    setStep(target);
    if (target === 5) void review();
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[220px_1fr]">
      <ol className="flex gap-1 overflow-x-auto lg:flex-col" aria-label="Etapas">
        {STEPS.map((label, i) => {
          const disabled = i === 4 && !spec.frontend;
          return (
            <li key={label}>
              <button
                onClick={() => goTo(i)}
                disabled={disabled}
                aria-current={step === i ? "step" : undefined}
                className={`flex w-full shrink-0 items-center gap-3 rounded-lg px-3 py-2 text-left text-sm disabled:opacity-40 ${
                  step === i ? "bg-primary/10 font-semibold" : "text-muted hover:bg-surface-2 hover:text-text"
                }`}
              >
                <span
                  className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${
                    i < step ? "bg-success text-white" : step === i ? "nova-gradient-bar text-white" : "bg-surface-2"
                  }`}
                >
                  {i < step ? "✓" : i + 1}
                </span>
                <span className="whitespace-nowrap">{label}</span>
              </button>
            </li>
          );
        })}
      </ol>

      <div className="flex min-w-0 flex-col gap-5">
        {error && <Alert tone={error.includes("regras simples") ? "info" : "danger"}>{error}</Alert>}

        {step === 0 && (
          <Card>
            <Title icon="sparkle" title="Como você quer criar o app?" intro="Nos dois modos você escolhe as mesmas tecnologias; muda quem preenche os campos de texto." />
            <div className="grid gap-4 md:grid-cols-2">
              <ChoiceCard
                selected={spec.mode === "COPILOT"}
                onClick={() => {
                  update({ mode: "COPILOT" });
                  setStep(1);
                }}
                icon={<NovaIcon name="copilot" size={32} gradient />}
                title="Copilot total"
                text="Descreva a ideia em poucas linhas. A IA propõe nome, objetivo, tecnologias, recursos e telas, e você só revisa."
              />
              <ChoiceCard
                selected={spec.mode === "ASSISTED"}
                onClick={() => {
                  update({ mode: "ASSISTED" });
                  setStep(1);
                }}
                icon={<NovaIcon name="assist" size={32} gradient />}
                title="Formulário assistido"
                text="Você preenche cada etapa e pede sugestão da IA nos campos de texto quando quiser."
              />
            </div>
          </Card>
        )}

        {step === 1 && (
          <>
            {spec.mode === "COPILOT" && (
              <Card>
                <Title icon="copilot" title="Descreva a ideia" intro="Quanto mais específico, melhor a proposta. Você revisa tudo antes de gerar." />
                <Textarea
                  aria-label="Ideia do app"
                  placeholder="Ex.: uma loja online de produtos naturais com login de clientes, busca e upload de fotos dos produtos"
                  value={idea}
                  maxLength={4000}
                  onChange={(e) => setIdea(e.target.value)}
                />
                <div className="mt-3 flex flex-wrap items-center gap-3">
                  <Button icon="sparkle" onClick={runCopilot} disabled={busy === "copilot" || idea.trim().length < 10}>
                    {busy === "copilot" ? "Gerando proposta…" : proposal ? "Gerar outra proposta" : "Gerar proposta com o Copilot"}
                  </Button>
                  <span className="text-xs text-muted">O texto é enviado ao provedor de IA do NOVA para gerar a proposta.</span>
                </div>
                {proposal && (
                  <div className="mt-4 flex flex-col gap-3">
                    {proposal.notice && <Alert tone="info">{proposal.notice}</Alert>}
                    {proposal.rationale && <Alert tone="success">{proposal.rationale}</Alert>}
                    {proposal.requirements.length > 0 && (
                      <details className="rounded-xl border border-border p-3 text-sm">
                        <summary className="cursor-pointer font-semibold">Requisitos propostos ({proposal.requirements.length})</summary>
                        <ul className="mt-2 list-disc pl-5 text-muted">
                          {proposal.requirements.map((r) => (
                            <li key={r}>{r}</li>
                          ))}
                        </ul>
                      </details>
                    )}
                    {proposal.backlog.length > 0 && (
                      <details className="rounded-xl border border-border p-3 text-sm">
                        <summary className="cursor-pointer font-semibold">Backlog inicial ({proposal.backlog.length})</summary>
                        <ul className="mt-2 flex flex-col gap-2 text-muted">
                          {proposal.backlog.map((b) => (
                            <li key={b.title}>
                              <span className="font-semibold text-text">{b.title}:</span> {b.story}
                            </li>
                          ))}
                        </ul>
                      </details>
                    )}
                  </div>
                )}
              </Card>
            )}
            <Card>
              <Title icon="form" title="Projeto" intro="Nome, descrição e objetivo do app." />
              <div className="flex flex-col gap-4">
                <Field label="Nome do projeto" htmlFor="name">
                  <Input id="name" value={spec.name} maxLength={80} onChange={(e) => update({ name: e.target.value })} />
                </Field>
                <Field label="Tipo" htmlFor="type-WEB_APP">
                  <div className="grid gap-3 sm:grid-cols-2">
                    {catalog.projectTypes.map((t) => (
                      <ChoiceCard
                        key={t.id}
                        id={`type-${t.id}`}
                        selected={spec.type === t.id}
                        onClick={() => update({ type: t.id })}
                        icon={<NovaIcon name={t.id === "APP" ? "screens" : "layout"} size={24} gradient={spec.type === t.id} />}
                        title={t.name}
                        text={t.description}
                      />
                    ))}
                  </div>
                </Field>
                <Field label="Descrição" htmlFor="description" hint="Uma frase sobre o que o app faz." tag={<AiButton busy={busy === "description"} onClick={() => assist("description")} />}>
                  <Input id="description" value={spec.description ?? ""} maxLength={500} onChange={(e) => update({ description: e.target.value })} />
                </Field>
                <Field label="Objetivo do app" htmlFor="objective" hint="Para quem é e que problema resolve." tag={<AiButton busy={busy === "objective"} onClick={() => assist("objective")} />}>
                  <Textarea id="objective" value={spec.objective ?? ""} maxLength={4000} onChange={(e) => update({ objective: e.target.value })} />
                </Field>
              </div>
            </Card>
          </>
        )}

        {step === 2 && (
          <>
            <Card>
              <Title icon="server" title="Backend" intro="Framework, versão, linguagem e ferramenta de build." />
              <OptionGrid>
                {catalog.backends.map((b) => (
                  <TechCard key={b.id} icons={icons} slug={b.icon} name={b.name} text={b.description} selected={backend.id === b.id} disabled={b.status !== "available"} onClick={() => chooseBackend(b.id)} />
                ))}
              </OptionGrid>
              <div className="mt-4 grid gap-4 md:grid-cols-3">
                <SelectField id="backend-version" label={`Versão do ${backend.name}`} value={spec.backend.version ?? ""} options={backend.versions} onChange={(v) => update({ backend: { ...spec.backend, version: v } })} />
                <SelectField
                  id="language"
                  label="Linguagem"
                  value={spec.backend.language ?? ""}
                  options={backend.languages ?? []}
                  labels={Object.fromEntries(catalog.languages.map((l) => [l.id, l.name]))}
                  onChange={(v) => update({ backend: { ...spec.backend, language: v, languageVersion: catalog.languages.find((l) => l.id === v)?.defaultVersion } })}
                />
                <SelectField id="language-version" label={`Versão de ${language?.name ?? "linguagem"}`} value={spec.backend.languageVersion ?? ""} options={language?.versions ?? []} onChange={(v) => update({ backend: { ...spec.backend, languageVersion: v } })} />
              </div>
              <p className="mb-2 mt-4 text-sm font-semibold">Ferramenta de build</p>
              <div className="grid gap-3 md:grid-cols-2">
                {backend.buildTools.map((b) => (
                  <ChoiceCard
                    key={b.id}
                    selected={spec.backend.buildTool === b.id}
                    onClick={() => update({ backend: { ...spec.backend, buildTool: b.id, packages: (spec.backend.packages ?? []).filter((id) => pkgAllowed(backend.packages.find((p) => p.id === id), b.id)) } })}
                    icon={<CatalogIcon icons={icons} slug={b.icon} name={b.name} size={32} />}
                    title={b.name}
                    text={
                      <>
                        Compilar: <code className="rounded bg-surface-2 px-1">{b.build}</code>
                      </>
                    }
                  />
                ))}
              </div>
            </Card>
            <Card>
              <Title icon="layout" title="Frontend" intro="A interface do app. Pode ficar sem frontend se for só uma API." />
              <OptionGrid>
                <TechCard icons={icons} slug="" name="Sem frontend" text="Só a API." selected={!spec.frontend} onClick={() => chooseFrontend(null)} plain />
                {catalog.frontends.map((f) => (
                  <TechCard key={f.id} icons={icons} slug={f.icon} name={f.name} text={f.description} selected={spec.frontend?.id === f.id} disabled={f.status !== "available"} onClick={() => chooseFrontend(f.id)} />
                ))}
              </OptionGrid>
              {frontend && (
                <div className="mt-4 max-w-xs">
                  <SelectField id="frontend-version" label={`Versão do ${frontend.name}`} value={spec.frontend?.version ?? ""} options={frontend.versions} onChange={(v) => update({ frontend: { ...spec.frontend!, version: v } })} />
                </div>
              )}
            </Card>
            <Card>
              <Title icon="database" title="Banco de dados" intro="A fonte central de dados do app." />
              <OptionGrid>
                <TechCard icons={icons} slug="" name="Sem banco" text="Sem persistência." selected={!spec.database} onClick={() => update({ database: null })} plain />
                {catalog.databases.map((d) => (
                  <TechCard key={d.id} icons={icons} slug={d.icon} name={d.name} text={d.description} selected={spec.database?.id === d.id} disabled={d.status !== "available"} onClick={() => update({ database: { id: d.id, version: d.defaultVersion } })} />
                ))}
              </OptionGrid>
              {database && (
                <div className="mt-4 max-w-xs">
                  <SelectField id="database-version" label={`Versão do ${database.name}`} value={spec.database?.version ?? ""} options={database.versions} onChange={(v) => update({ database: { id: database.id, version: v } })} />
                </div>
              )}
            </Card>
            <Card>
              <Title icon="sparkle" title="IA externa" intro="Use se o próprio app precisar de IA para os usuários dele." />
              <OptionGrid>
                <TechCard icons={icons} slug="" name="Sem IA" text="O app não usa IA." selected={!spec.ai} onClick={() => update({ ai: null })} plain />
                {catalog.aiProviders.map((a) => (
                  <TechCard key={a.id} icons={icons} slug={a.icon} name={a.name} text={a.description} selected={spec.ai?.provider === a.id} disabled={a.status !== "available"} onClick={() => update({ ai: { provider: a.id, model: a.defaultModel } })} />
                ))}
              </OptionGrid>
              {ai && (
                <div className="mt-4 max-w-xs">
                  <SelectField id="ai-model" label="Modelo" value={spec.ai?.model ?? ""} options={ai.models} onChange={(v) => update({ ai: { provider: ai.id, model: v } })} />
                </div>
              )}
            </Card>
          </>
        )}

        {step === 3 && (
          <>
            <Card>
              <Title icon="sliders" title="Recursos" intro="Cada recurso adiciona os pacotes, as configurações e o código de que precisa." />
              <ul className="divide-y divide-border">
                {catalog.features.map((f) => (
                  <li key={f.id} className="flex items-center gap-4 py-3">
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold">{f.name}</p>
                      <p className="text-xs text-muted">{f.description}</p>
                    </div>
                    <Toggle id={`feature-${f.id}`} label={f.name} checked={spec.features.includes(f.id)} onChange={(on) => update({ features: on ? [...spec.features, f.id] : spec.features.filter((x) => x !== f.id) })} />
                  </li>
                ))}
              </ul>
            </Card>
            <PackageCard
              title={`Pacotes e plugins do ${backend.name}`}
              stack={backend}
              selected={spec.backend.packages ?? []}
              features={spec.features}
              hasDatabase={!!spec.database}
              buildTool={spec.backend.buildTool ?? ""}
              onChange={(packages) => update({ backend: { ...spec.backend, packages } })}
            />
            {frontend && (
              <PackageCard
                title={`Pacotes do ${frontend.name}`}
                stack={frontend}
                selected={spec.frontend?.packages ?? []}
                features={spec.features}
                hasDatabase={false}
                buildTool={frontend.defaultBuildTool ?? ""}
                onChange={(packages) => update({ frontend: { ...spec.frontend!, packages } })}
              />
            )}
            <Card>
              <Title icon="plug" title="Conectores opcionais" intro="O banco continua sendo a fonte central. Ative um conector só se o app precisar dele." />
              <div className="grid gap-3 md:grid-cols-2">
                {catalog.connectors.map((c) => {
                  const on = spec.connectors.includes(c.id);
                  return (
                    <div key={c.id} className={`flex items-center gap-3 rounded-xl border p-3 ${on ? "border-primary bg-primary/5" : "border-border"}`}>
                      <CatalogIcon icons={icons} slug={c.icon} name={c.name} size={36} />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-semibold">{c.name}</p>
                        <p className="text-xs text-muted">{c.description}</p>
                      </div>
                      <Toggle id={`connector-${c.id}`} label={c.name} checked={on} onChange={(v) => update({ connectors: v ? [...spec.connectors, c.id] : spec.connectors.filter((x) => x !== c.id) })} />
                    </div>
                  );
                })}
              </div>
            </Card>
          </>
        )}

        {step === 4 && spec.frontend && (
          <Card>
            <Title icon="screens" title="Telas" intro="Liste as telas do app. Cada uma vira uma rota inicial no frontend." />
            <ScreensEditor screens={spec.screens} onChange={(screens) => update({ screens })} />
            <div className="mt-3">
              <AiButton busy={busy === "screens"} onClick={() => assist("screens")} label="Sugerir telas com IA" />
            </div>
          </Card>
        )}

        {step === 5 && (
          <ReviewStep plan={plan} loading={busy === "plan"} icons={icons} catalog={catalog} buildToolName={buildTool?.name ?? ""} />
        )}

        <div className="flex flex-wrap justify-between gap-3">
          <Button variant="secondary" icon="chevronLeft" onClick={() => goTo(step - 1)} disabled={step === 0}>
            Voltar
          </Button>
          {step < 5 ? (
            step > 0 && (
              <Button onClick={() => goTo(step + 1)}>
                Continuar <NovaIcon name="chevronRight" size={16} />
              </Button>
            )
          ) : (
            <Button icon="sparkle" onClick={generate} disabled={!plan || busy === "generate"}>
              {busy === "generate" ? "Gerando projeto…" : "Gerar projeto"}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

function pkgAllowed(pkg: Pkg | undefined, buildTool: string): boolean {
  return !pkg?.buildTools?.length || pkg.buildTools.includes(buildTool);
}

function Title({ icon, title, intro }: { icon: NovaIconName; title: string; intro: string }) {
  return (
    <div className="mb-4">
      <h2 className="flex items-center gap-2 text-lg font-bold">
        <NovaIcon name={icon} gradient />
        {title}
      </h2>
      <p className="mt-1 text-sm text-muted">{intro}</p>
    </div>
  );
}

function OptionGrid({ children }: { children: ReactNode }) {
  return <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{children}</div>;
}

function ChoiceCard({ selected, onClick, icon, title, text, id }: { selected: boolean; onClick: () => void; icon: ReactNode; title: string; text: ReactNode; id?: string }) {
  return (
    <button
      id={id}
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onClick}
      className={`flex items-start gap-3 rounded-xl border p-4 text-left transition ${selected ? "border-primary bg-primary/5 ring-2 ring-[var(--ring)]" : "border-border hover:bg-surface-2"}`}
    >
      <span className="shrink-0">{icon}</span>
      <span className="min-w-0">
        <span className="block text-sm font-semibold">{title}</span>
        <span className="block text-xs text-muted">{text}</span>
      </span>
    </button>
  );
}

function TechCard({
  icons,
  slug,
  name,
  text,
  selected,
  disabled,
  onClick,
  plain,
}: {
  icons: Record<string, IconData>;
  slug: string;
  name: string;
  text: string;
  selected: boolean;
  disabled?: boolean;
  onClick: () => void;
  plain?: boolean;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      disabled={disabled}
      onClick={onClick}
      className={`relative flex items-center gap-3 rounded-xl border p-3 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ${
        selected ? "border-primary bg-primary/5 ring-2 ring-[var(--ring)]" : "border-border hover:bg-surface-2"
      }`}
    >
      {plain ? (
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-border text-muted">
          <NovaIcon name="close" size={18} />
        </span>
      ) : (
        <CatalogIcon icons={icons} slug={slug} name={name} size={36} />
      )}
      <span className={`min-w-0 ${disabled ? "pr-14" : ""}`}>
        <span className="block truncate text-sm font-semibold">{name}</span>
        <span className="block text-xs text-muted">{text}</span>
      </span>
      {disabled && <span className="absolute right-2 top-2 rounded-full bg-surface-2 px-1.5 py-0.5 text-[10px] font-semibold text-muted">em breve</span>}
    </button>
  );
}

function SelectField({ id, label, value, options, labels, onChange }: { id: string; label: string; value: string; options: string[]; labels?: Record<string, string>; onChange: (v: string) => void }) {
  return (
    <Field label={label} htmlFor={id}>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm">
        {options.map((o) => (
          <option key={o} value={o}>
            {labels?.[o] ?? o}
          </option>
        ))}
      </select>
    </Field>
  );
}

function AiButton({ busy, onClick, label = "Sugerir com IA" }: { busy: boolean; onClick: () => void; label?: string }) {
  return (
    <button type="button" onClick={onClick} disabled={busy} className="inline-flex items-center gap-1 rounded-full border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary disabled:opacity-60">
      <NovaIcon name="sparkle" size={12} />
      {busy ? "Pensando…" : label}
    </button>
  );
}

function PackageCard({
  title,
  stack,
  selected,
  features,
  hasDatabase,
  buildTool,
  onChange,
}: {
  title: string;
  stack: Stack;
  selected: string[];
  features: string[];
  hasDatabase: boolean;
  buildTool: string;
  onChange: (ids: string[]) => void;
}) {
  const groups: { label: string; kind: Pkg["kind"] }[] = [
    { label: "Dependências", kind: "dependency" },
    { label: "Plugins de build", kind: "plugin" },
  ];
  return (
    <Card>
      <Title icon="chip" title={title} intro="Obrigatórios já vêm marcados. Os demais você liga ou desliga; a revisão mostra a lista final com as versões." />
      {groups.map((g) => {
        const items = stack.packages.filter((p) => p.kind === g.kind);
        if (!items.length) return null;
        return (
          <div key={g.kind} className="mb-4 last:mb-0">
            <p className="mb-2 text-xs font-bold uppercase tracking-wide text-muted">{g.label}</p>
            <ul className="grid gap-2 md:grid-cols-2">
              {items.map((p) => {
                const byFeature = !!p.feature && features.includes(p.feature);
                const blocked = (p.needs === "database" && !hasDatabase) || !pkgAllowed(p, buildTool);
                const checked = !blocked && (p.required || byFeature || selected.includes(p.id));
                const locked = p.required || byFeature || blocked || !!p.feature;
                const note = p.required ? "obrigatório" : byFeature ? "ativado por recurso" : p.feature ? `ative o recurso “${p.feature}”` : blocked ? (p.needs === "database" && !hasDatabase ? "precisa de banco" : `indisponível em ${buildTool}`) : null;
                return (
                  <li key={p.id}>
                    <label className={`flex items-start gap-3 rounded-lg border border-border p-3 ${locked ? "opacity-80" : "cursor-pointer hover:bg-surface-2"}`}>
                      <input
                        type="checkbox"
                        className="mt-1 h-4 w-4 accent-[var(--primary)]"
                        checked={checked}
                        disabled={locked}
                        onChange={(e) => onChange(e.target.checked ? [...selected, p.id] : selected.filter((x) => x !== p.id))}
                      />
                      <span className="min-w-0">
                        <span className="flex flex-wrap items-center gap-2 text-sm font-semibold">
                          {p.name}
                          {note && <span className="rounded-full bg-surface-2 px-1.5 py-0.5 text-[10px] font-semibold text-muted">{note}</span>}
                        </span>
                        <span className="block text-xs text-muted">{p.description}</span>
                        <code className="block truncate text-[11px] text-muted">{p.artifact}</code>
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
    </Card>
  );
}

function ScreensEditor({ screens, onChange }: { screens: string[]; onChange: (s: string[]) => void }) {
  const [draft, setDraft] = useState("");
  function add() {
    const name = draft.trim();
    if (name && !screens.includes(name) && screens.length < 30) onChange([...screens, name]);
    setDraft("");
  }
  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-2">
        <Input
          aria-label="Nome da tela"
          placeholder="Ex.: Produtos"
          value={draft}
          maxLength={60}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add();
            }
          }}
        />
        <Button variant="secondary" onClick={add}>
          Adicionar
        </Button>
      </div>
      {screens.length === 0 ? (
        <p className="text-sm text-muted">Nenhuma tela ainda. O frontend é gerado só com a página inicial.</p>
      ) : (
        <ol className="flex flex-col gap-2">
          {screens.map((s, i) => (
            <li key={s} className="flex items-center gap-3 rounded-lg border border-border px-3 py-2 text-sm">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-surface-2 text-xs font-bold">{i + 1}</span>
              <span className="flex-1">{s}</span>
              <button aria-label={`Remover ${s}`} onClick={() => onChange(screens.filter((x) => x !== s))} className="rounded p-1 text-muted hover:bg-surface-2 hover:text-danger">
                <NovaIcon name="trash" size={16} />
              </button>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function ReviewStep({ plan, loading, icons, catalog, buildToolName }: { plan: Plan | null; loading: boolean; icons: Record<string, IconData>; catalog: Catalog; buildToolName: string }) {
  const stackItems = useMemo(() => {
    if (!plan) return [];
    const s = plan.spec;
    const find = <T extends { id: string }>(list: T[], id?: string | null) => list.find((x) => x.id === id);
    const backend = find(catalog.backends, s.backend.id);
    const lang = find(catalog.languages, s.backend.language);
    const frontend = find(catalog.frontends, s.frontend?.id);
    const db = find(catalog.databases, s.database?.id);
    const ai = find(catalog.aiProviders, s.ai?.provider);
    return [
      backend && { label: "Backend", name: backend.name, icon: backend.icon, version: s.backend.version },
      lang && { label: "Linguagem", name: lang.name, icon: lang.icon, version: s.backend.languageVersion },
      { label: "Build", name: buildToolName, icon: backend?.buildTools.find((b) => b.id === s.backend.buildTool)?.icon ?? "", version: "" },
      frontend && { label: "Frontend", name: frontend.name, icon: frontend.icon, version: s.frontend?.version },
      db && { label: "Banco", name: db.name, icon: db.icon, version: s.database?.version },
      ...s.connectors.map((c) => {
        const conn = find(catalog.connectors, c);
        return conn && { label: "Conector", name: conn.name, icon: conn.icon, version: "" };
      }),
      ai && { label: "IA externa", name: ai.name, icon: ai.icon, version: s.ai?.model },
    ].filter(Boolean) as { label: string; name: string; icon: string; version?: string | null }[];
  }, [plan, catalog, buildToolName]);

  if (loading) return <Card>Calculando o plano…</Card>;
  if (!plan) return <Card>Revise as etapas anteriores para ver o plano.</Card>;

  return (
    <>
      {plan.warnings.map((w) => (
        <Alert key={w} tone="warning">
          {w}
        </Alert>
      ))}
      <Card>
        <Title icon="layers" title={plan.spec.name} intro={plan.spec.description ?? "Confira as escolhas antes de gerar."} />
        <ul className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
          {stackItems.map((item, i) => (
            <li key={`${item.label}-${i}`} className="flex items-center gap-3 rounded-xl border border-border p-3">
              <CatalogIcon icons={icons} slug={item.icon} name={item.name} size={36} />
              <span className="min-w-0">
                <span className="block text-[11px] font-bold uppercase tracking-wide text-muted">{item.label}</span>
                <span className="block truncate text-sm font-semibold">
                  {item.name} {item.version}
                </span>
              </span>
            </li>
          ))}
        </ul>
      </Card>
      <Card>
        <Title icon="rocket" title="Como compilar e rodar" intro="Estes comandos também vão no README do projeto." />
        <div className="grid gap-4 md:grid-cols-2">
          {[plan.backend, plan.frontend].map(
            (side, i) =>
              side && (
                <div key={side.id} className="rounded-xl bg-surface-2 p-3 text-sm">
                  <p className="mb-2 font-semibold">
                    {i === 0 ? "Backend" : "Frontend"} · {side.commands.tool}
                  </p>
                  {(["build", "run", "test"] as const).map((k) => (
                    <p key={k} className="flex gap-2">
                      <span className="w-16 shrink-0 text-muted">{k === "build" ? "Compilar" : k === "run" ? "Rodar" : "Testar"}</span>
                      <code className="break-all">{side.commands[k]}</code>
                    </p>
                  ))}
                </div>
              ),
          )}
        </div>
      </Card>
      {[plan.backend, plan.frontend].map(
        (side, i) =>
          side && (
            <Card key={side.id}>
              <Title icon="chip" title={`Dependências e plugins do ${i === 0 ? "backend" : "frontend"}`} intro={`${side.dependencies.length} dependências e ${side.plugins.length} plugins.`} />
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] text-left text-sm">
                  <thead className="text-xs uppercase tracking-wide text-muted">
                    <tr>
                      <th className="py-2 pr-3">Pacote</th>
                      <th className="py-2 pr-3">Tipo</th>
                      <th className="py-2 pr-3">Escopo</th>
                      <th className="py-2">Motivo</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {[...side.dependencies, ...side.plugins].map((a) => (
                      <tr key={a.coordinate}>
                        <td className="py-2 pr-3">
                          <code className="break-all text-xs">{a.coordinate}</code>
                        </td>
                        <td className="py-2 pr-3">{a.kind === "plugin" ? "Plugin" : "Dependência"}</td>
                        <td className="py-2 pr-3">{a.scope}</td>
                        <td className="py-2 text-muted">{a.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          ),
      )}
      {plan.env.length > 0 && (
        <Card>
          <Title icon="key" title="Variáveis de ambiente" intro="Segredos nunca vão para o código: o projeto só referencia os nomes." />
          <ul className="grid gap-2 md:grid-cols-2">
            {plan.env.map((e) => (
              <li key={e.name} className="flex items-center justify-between gap-3 rounded-lg border border-border px-3 py-2 text-sm">
                <span className="min-w-0">
                  <code className="font-semibold">{e.name}</code>
                  <span className="block text-xs text-muted">{e.usedBy}</span>
                </span>
                {e.secret && <span className="rounded-full bg-warning/15 px-2 py-0.5 text-[11px] font-bold text-warning">segredo</span>}
              </li>
            ))}
          </ul>
        </Card>
      )}
      <Card>
        <Title icon="tree" title={`Arquivos que serão gerados (${plan.files.length})`} intro="Estrutura inicial do projeto." />
        <pre className="max-h-80 overflow-auto rounded-xl bg-surface-2 p-3 text-xs leading-relaxed">{plan.files.join("\n")}</pre>
      </Card>
    </>
  );
}
