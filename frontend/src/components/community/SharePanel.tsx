"use client";

import { useEffect, useState } from "react";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { formatDate } from "@/components/projects/format";
import { Alert, Button, Card, Field, Input, Textarea } from "@/components/ui/ui";
import { LICENSES, VISIBILITY, type License, type SharingState, type Visibility } from "@/lib/community-types";
import { call, errorText } from "./client";

/*
 * Aba "Compartilhar" do projeto (Objetivo 3). O projeto nasce privado; ampliar a visibilidade pede a senha,
 * confere se há credenciais nos arquivos visíveis, exige licença para entrar na rede e o nome digitado para tornar público.
 */

const ORDER: Visibility[] = ["PRIVATE", "LINK", "COMMUNITY", "PUBLIC"];

export function SharePanel({ projectId, projectName, disabled, onVisibility }: { projectId: string; projectName: string; disabled: boolean; onVisibility: (v: Visibility) => void }) {
  const base = `/api/backend/projects/${projectId}/sharing`;
  const [state, setState] = useState<SharingState | null>(null);
  const [visibility, setVisibility] = useState<Visibility>("PRIVATE");
  const [license, setLicense] = useState<License | "">("");
  const [hidden, setHidden] = useState("");
  const [password, setPassword] = useState("");
  const [confirmName, setConfirmName] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const [days, setDays] = useState("7");
  const [newLink, setNewLink] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  function apply(s: SharingState) {
    setState(s);
    setVisibility(s.visibility);
    setLicense(s.license ?? "");
    setHidden(s.hiddenPaths.join("\n"));
    setPassword("");
    setConfirmName("");
  }

  useEffect(() => {
    let alive = true;
    void call<SharingState>(base).then((r) => {
      if (alive && r.ok) apply(r.data);
    });
    return () => {
      alive = false;
    };
  }, [base]);

  if (!state) return <p className="text-sm text-muted">Carregando…</p>;

  const widening = ORDER.indexOf(visibility) > ORDER.indexOf(state.visibility);
  const listed = visibility === "COMMUNITY" || visibility === "PUBLIC";
  const missing = (listed && !license) || (widening && !password) || (widening && visibility === "PUBLIC" && confirmName !== projectName);

  async function save() {
    setBusy(true);
    setMessage(null);
    const result = await call<SharingState>(base, "PUT", {
      visibility,
      license: license || null,
      hiddenPaths: hidden.split("\n").map((l) => l.trim()).filter(Boolean),
      currentPassword: password || null,
      confirmName: confirmName || null,
    });
    setBusy(false);
    if (result.ok) {
      apply(result.data);
      onVisibility(result.data.visibility);
      setNewLink(null);
      setMessage({ tone: "success", text: `Visibilidade salva: ${VISIBILITY[result.data.visibility].label}.` });
    } else {
      setMessage({ tone: "danger", text: errorText(result.problem) });
    }
  }

  async function createLink() {
    const result = await call<{ token: string }>(`${base}/links`, "POST", { expiresInDays: days ? Number(days) : null });
    if (result.ok) {
      setNewLink(`${window.location.origin}/s/${result.data.token}`);
      setCopied(false);
      const fresh = await call<SharingState>(base);
      if (fresh.ok) setState(fresh.data);
    } else {
      setMessage({ tone: "danger", text: errorText(result.problem) });
    }
  }

  async function revoke(id: string) {
    const result = await call(`${base}/links/${id}`, "DELETE");
    if (result.ok) {
      setState({ ...state!, links: state!.links.filter((l) => l.id !== id) });
      setNewLink(null);
    } else {
      setMessage({ tone: "danger", text: errorText(result.problem) });
    }
  }

  return (
    <div className="flex max-w-3xl flex-col gap-5">
      {disabled && <Alert tone="warning">Projeto arquivado ou na lixeira não pode ser compartilhado. Reative-o em Configurações.</Alert>}
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <Card>
        <h2 className="mb-1 font-bold">Quem pode ver</h2>
        <p className="mb-3 text-sm text-muted">Arquivos de segredos (<code>.env</code>) nunca são compartilhados, em nenhuma opção.</p>
        <div className="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Visibilidade">
          {ORDER.map((v) => (
            <button
              key={v}
              type="button"
              role="radio"
              aria-checked={visibility === v}
              disabled={disabled && v !== "PRIVATE"}
              onClick={() => setVisibility(v)}
              className={`flex items-start gap-3 rounded-xl border p-3 text-left transition disabled:opacity-50 ${visibility === v ? "border-primary bg-primary/5" : "border-border hover:bg-surface-2"}`}
            >
              <NovaIcon name={VISIBILITY[v].icon} gradient={visibility === v} className="mt-0.5 shrink-0" />
              <span>
                <span className="block text-sm font-semibold">
                  {VISIBILITY[v].label}
                  {state.visibility === v && <span className="ml-2 text-[11px] font-normal text-muted">atual</span>}
                </span>
                <span className="block text-xs text-muted">{VISIBILITY[v].description}</span>
              </span>
            </button>
          ))}
        </div>

        <div className="mt-4 flex flex-col gap-4">
          {listed && (
            <Field label="Licença" htmlFor="share-license" hint={license ? LICENSES[license].description : "Diz o que as outras pessoas podem fazer com o seu código."}>
              <select
                id="share-license"
                value={license}
                onChange={(e) => setLicense(e.target.value as License | "")}
                className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm"
              >
                <option value="">Escolha…</option>
                {(Object.keys(LICENSES) as License[]).map((l) => (
                  <option key={l} value={l}>
                    {LICENSES[l].label}
                  </option>
                ))}
              </select>
            </Field>
          )}
          {visibility !== "PRIVATE" && (
            <Field label="Arquivos e pastas ocultos" htmlFor="share-hidden" hint="Um caminho por linha. Só você continua vendo; não entram em downloads nem em forks de outras pessoas.">
              <Textarea id="share-hidden" rows={3} value={hidden} onChange={(e) => setHidden(e.target.value)} placeholder="ex.: docs/interno" />
            </Field>
          )}
          {widening && (
            <>
              <Alert tone="info">
                Antes de ampliar, o NOVA procura senhas e chaves nos arquivos que ficarão visíveis. Se encontrar, a publicação é bloqueada e você vê onde está.
              </Alert>
              <Field label="Sua senha atual" htmlFor="share-password" hint="Confirma que é você quem está ampliando a visibilidade.">
                <Input id="share-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
              </Field>
              {visibility === "PUBLIC" && (
                <Field label={`Digite o nome do projeto: ${projectName}`} htmlFor="share-confirm">
                  <Input id="share-confirm" value={confirmName} onChange={(e) => setConfirmName(e.target.value)} />
                </Field>
              )}
            </>
          )}
          <div>
            <Button onClick={save} disabled={busy || missing}>
              {busy ? "Verificando…" : "Salvar visibilidade"}
            </Button>
          </div>
        </div>
      </Card>

      {state.visibility !== "PRIVATE" && (
        <Card>
          <h2 className="mb-1 font-bold">Links de compartilhamento</h2>
          <p className="mb-3 text-sm text-muted">Quem abrir o link vê o projeto sem precisar de conta. Você pode revogar quando quiser.</p>
          <div className="flex flex-wrap items-end gap-2">
            <Field label="Validade" htmlFor="link-days">
              <select id="link-days" value={days} onChange={(e) => setDays(e.target.value)} className="rounded-lg border border-border bg-surface px-3 py-2 text-sm">
                <option value="1">1 dia</option>
                <option value="7">7 dias</option>
                <option value="30">30 dias</option>
                <option value="">Sem validade</option>
              </select>
            </Field>
            <Button variant="secondary" icon="share" onClick={createLink}>
              Criar link
            </Button>
          </div>
          {newLink && (
            <div className="mt-3 flex flex-col gap-2">
              <Alert tone="warning">Copie agora: por segurança, este endereço não será mostrado de novo.</Alert>
              <div className="flex flex-wrap gap-2">
                <Input readOnly aria-label="Link criado" value={newLink} onFocus={(e) => e.currentTarget.select()} className="min-w-0 flex-1 font-mono text-xs" />
                <Button
                  variant="secondary"
                  onClick={async () => {
                    await navigator.clipboard?.writeText(newLink).catch(() => undefined);
                    setCopied(true);
                  }}
                >
                  {copied ? "Copiado" : "Copiar"}
                </Button>
              </div>
            </div>
          )}
          {state.links.length > 0 && (
            <ul className="mt-4 divide-y divide-border text-sm">
              {state.links.map((l) => (
                <li key={l.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                  <span className="text-xs text-muted">
                    Criado em {formatDate(l.createdAt)} · {l.expiresAt ? `expira em ${formatDate(l.expiresAt)}` : "sem validade"} ·{" "}
                    {l.lastUsedAt ? `último acesso em ${formatDate(l.lastUsedAt)}` : "ainda não usado"}
                  </span>
                  <Button variant="ghost" onClick={() => revoke(l.id)}>
                    Revogar
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
    </div>
  );
}
