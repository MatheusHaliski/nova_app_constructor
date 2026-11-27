"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { NovaIcon, type NovaIconName } from "@/components/icons/NovaIcon";
import { Alert, Button, Card, Field, Input, LgpdTag, Textarea, Toggle } from "@/components/ui/ui";
import type { Dict } from "@/lib/i18n";
import type { Lang, Theme } from "@/lib/prefs";
import { callMe, type Problem } from "./client";
import type { ConsentPurpose, Section, Settings, Visibility } from "./types";

/*
 * Configurações do perfil no padrão da aula "LGPD em redes sociais": cada input, toggle e botão
 * mostra o artigo da LGPD que realiza. Padrões mais protetivos, um consentimento por finalidade,
 * revogação com o mesmo esforço da concessão e confirmação dupla para ações destrutivas.
 */

type Props = { section: Section; initial: Settings; t: Dict; lang: Lang };

export function SettingsSection({ section, initial, t, lang }: Props) {
  const [settings, setSettings] = useState(initial);
  switch (section) {
    case "conta":
      return <AccountSection settings={settings} onSaved={setSettings} t={t} />;
    case "privacidade":
      return <PrivacySection settings={settings} onSaved={setSettings} t={t} lang={lang} />;
    case "aparencia":
      return <AppearanceSection settings={settings} onSaved={setSettings} t={t} />;
    case "idioma":
      return <LanguageSection settings={settings} onSaved={setSettings} t={t} />;
    case "seus-dados":
      return <DataSection settings={settings} t={t} />;
  }
}

function SectionTitle({ icon, title, intro }: { icon: NovaIconName; title: string; intro: string }) {
  return (
    <div className="mb-5">
      <h2 className="flex items-center gap-2 text-lg font-bold">
        <NovaIcon name={icon} gradient />
        {title}
      </h2>
      <p className="mt-1 text-sm text-muted">{intro}</p>
    </div>
  );
}

function useFeedback() {
  const [message, setMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  return {
    message,
    ok: (text: string) => setMessage({ tone: "success", text }),
    fail: (problem: Problem, fallback: string) => setMessage({ tone: "danger", text: problem.detail ?? fallback }),
    clear: () => setMessage(null),
  };
}

/* ---------------- Conta: correção (art. 18, III) ---------------- */

function AccountSection({ settings, onSaved, t }: { settings: Settings; onSaved: (s: Settings) => void; t: Dict }) {
  const a = settings.account;
  const [form, setForm] = useState({ name: a.name, username: a.username, bio: a.bio ?? "", birthDate: a.birthDate, websiteUrl: a.websiteUrl ?? "", githubUsername: a.githubUsername ?? "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const [emailOpen, setEmailOpen] = useState(false);
  const feedback = useFeedback();

  async function save(e: FormEvent) {
    e.preventDefault();
    setPending(true);
    feedback.clear();
    const result = await callMe("/account", "PUT", { ...form, bio: form.bio || null, websiteUrl: form.websiteUrl || null, githubUsername: form.githubUsername || null });
    setPending(false);
    if (result.ok) {
      onSaved(result.data);
      setErrors({});
      feedback.ok(t.common.saved);
    } else {
      setErrors(result.problem.errors ?? {});
      feedback.fail(result.problem, t.common.error);
    }
  }

  function reset() {
    setForm({ name: a.name, username: a.username, bio: a.bio ?? "", birthDate: a.birthDate, websiteUrl: a.websiteUrl ?? "", githubUsername: a.githubUsername ?? "" });
    setErrors({});
    feedback.clear();
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <SectionTitle icon="user" title={t.settings.account} intro={t.settings.accountIntro} />
        <form onSubmit={save} className="flex flex-col gap-4" noValidate>
          {feedback.message && <Alert tone={feedback.message.tone}>{feedback.message.text}</Alert>}
          <div className="grid gap-4 md:grid-cols-2">
            <Field label={t.settings.displayName} htmlFor="name" error={errors.name} tag={<LgpdTag>Art. 18, III · Correção</LgpdTag>}>
              <Input id="name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} invalid={!!errors.name} />
            </Field>
            <Field label={t.settings.username} htmlFor="username" hint={t.settings.usernameHint} error={errors.username}>
              <Input
                id="username"
                value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value.toLowerCase() })}
                invalid={!!errors.username}
              />
            </Field>
          </div>
          <Field label={t.settings.email} htmlFor="email" tag={<LgpdTag>Art. 7º, V · Execução de contrato</LgpdTag>}>
            <div className="flex flex-wrap items-center gap-2">
              <Input id="email" value={a.email} readOnly className="flex-1" />
              <span
                className={`rounded-full px-2 py-1 text-[11px] font-bold ${
                  a.emailVerified ? "bg-success/15 text-success" : "bg-warning/15 text-warning"
                }`}
              >
                {a.emailVerified ? `✓ ${t.settings.verified}` : t.settings.notVerified}
              </span>
              <Button type="button" variant="secondary" icon="mail" onClick={() => setEmailOpen(true)}>
                {t.settings.changeEmail}
              </Button>
            </div>
          </Field>
          <div className="grid gap-4 md:grid-cols-2">
            <Field
              label={t.settings.birthDate}
              htmlFor="birthDate"
              hint={t.settings.birthDateHint}
              error={errors.birthDate}
              tag={<LgpdTag>Art. 6º, III · Necessidade</LgpdTag>}
            >
              <Input id="birthDate" type="date" value={form.birthDate} onChange={(e) => setForm({ ...form, birthDate: e.target.value })} />
            </Field>
            <Field label={t.settings.bio} htmlFor="bio" error={errors.bio} hint={`${form.bio.length}/280`}>
              <Textarea id="bio" maxLength={280} value={form.bio} onChange={(e) => setForm({ ...form, bio: e.target.value })} />
            </Field>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Site ou portfólio" htmlFor="websiteUrl" error={errors.websiteUrl} hint="Aparece no seu perfil público, se ele estiver visível.">
              <Input id="websiteUrl" type="url" maxLength={200} placeholder="https://" value={form.websiteUrl} onChange={(e) => setForm({ ...form, websiteUrl: e.target.value })} />
            </Field>
            <Field label="Usuário do GitHub" htmlFor="githubUsername" error={errors.githubUsername}>
              <Input id="githubUsername" maxLength={39} value={form.githubUsername} onChange={(e) => setForm({ ...form, githubUsername: e.target.value })} />
            </Field>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={pending}>
              {pending ? t.common.saving : t.common.save}
            </Button>
            <Button type="button" variant="secondary" onClick={reset}>
              {t.common.cancel}
            </Button>
          </div>
        </form>
      </Card>
      <PasswordCard t={t} />
      <SessionsCard lastLoginAt={a.lastLoginAt} passwordChangedAt={a.passwordChangedAt} />
      {emailOpen && <EmailDialog t={t} onClose={() => setEmailOpen(false)} onSaved={onSaved} />}
    </div>
  );
}

function PasswordCard({ t }: { t: Dict }) {
  const [form, setForm] = useState({ currentPassword: "", newPassword: "" });
  const [pending, setPending] = useState(false);
  const feedback = useFeedback();

  async function save(e: FormEvent) {
    e.preventDefault();
    setPending(true);
    const result = await callMe<null>("/password", "PUT", form);
    setPending(false);
    if (result.ok) {
      setForm({ currentPassword: "", newPassword: "" });
      feedback.ok(t.settings.passwordChanged);
    } else {
      feedback.fail(result.problem, t.common.error);
    }
  }

  return (
    <Card>
      <SectionTitle icon="key" title={t.settings.changePassword} intro={t.auth.passwordHint} />
      <form onSubmit={save} className="flex flex-col gap-4" noValidate>
        {feedback.message && <Alert tone={feedback.message.tone}>{feedback.message.text}</Alert>}
        <div className="grid gap-4 md:grid-cols-2">
          <Field label={t.settings.currentPassword} htmlFor="currentPassword">
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
            />
          </Field>
          <Field label={t.settings.newPassword} htmlFor="newPassword">
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
            />
          </Field>
        </div>
        <div>
          <Button type="submit" variant="secondary" disabled={pending}>
            {pending ? t.common.saving : t.settings.changePassword}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-6 shadow-2xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-bold">{title}</h3>
          <button onClick={onClose} aria-label={title} className="rounded-lg p-1 hover:bg-surface-2">
            <NovaIcon name="close" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

function EmailDialog({ t, onClose, onSaved }: { t: Dict; onClose: () => void; onSaved: (s: Settings) => void }) {
  const [form, setForm] = useState({ newEmail: "", currentPassword: "" });
  const [pending, setPending] = useState(false);
  const feedback = useFeedback();

  async function save(e: FormEvent) {
    e.preventDefault();
    setPending(true);
    const result = await callMe("/email", "PUT", form);
    setPending(false);
    if (result.ok) {
      onSaved(result.data);
      onClose();
    } else {
      feedback.fail(result.problem, t.common.error);
    }
  }

  return (
    <Dialog title={t.settings.changeEmail} onClose={onClose}>
      <form onSubmit={save} className="flex flex-col gap-4" noValidate>
        {feedback.message && <Alert tone={feedback.message.tone}>{feedback.message.text}</Alert>}
        <Field label={t.settings.newEmail} htmlFor="newEmail">
          <Input id="newEmail" type="email" value={form.newEmail} onChange={(e) => setForm({ ...form, newEmail: e.target.value })} />
        </Field>
        <Field label={t.settings.currentPassword} htmlFor="emailPassword">
          <Input
            id="emailPassword"
            type="password"
            autoComplete="current-password"
            value={form.currentPassword}
            onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
          />
        </Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button type="submit" disabled={pending}>
            {pending ? t.common.saving : t.common.save}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/* ---------------- Privacidade: compartilhamento (art. 18, VII) e consentimentos (art. 7º, I) ---------------- */

const CONSENTS: { purpose: ConsentPurpose; tag: string; icon: NovaIconName; label: Record<Lang, string>; hint: Record<Lang, string> }[] = [
  {
    purpose: "PERSONALIZED_FEED",
    tag: "Art. 7º, I · Consentimento revogável",
    icon: "feed",
    label: { "pt-BR": "Feed personalizado", "en-US": "Personalized feed", "es-ES": "Feed personalizado" },
    hint: {
      "pt-BR": "Usa seu histórico de uso para recomendar projetos.",
      "en-US": "Uses your activity to recommend projects.",
      "es-ES": "Usa tu historial para recomendar proyectos.",
    },
  },
  {
    purpose: "PRODUCT_ANALYTICS",
    tag: "Art. 7º, I · Consentimento revogável",
    icon: "diagram",
    label: { "pt-BR": "Métricas de uso", "en-US": "Usage analytics", "es-ES": "Métricas de uso" },
    hint: {
      "pt-BR": "Cookies não essenciais para melhorar o produto.",
      "en-US": "Non-essential cookies to improve the product.",
      "es-ES": "Cookies no esenciales para mejorar el producto.",
    },
  },
  {
    purpose: "MARKETING_EMAILS",
    tag: "Art. 7º, I · Consentimento revogável",
    icon: "mail",
    label: { "pt-BR": "E-mails de novidades", "en-US": "News emails", "es-ES": "Correos de novedades" },
    hint: {
      "pt-BR": "Novidades e conteúdos do NOVA por e-mail.",
      "en-US": "NOVA news and content by email.",
      "es-ES": "Novedades y contenidos de NOVA por correo.",
    },
  },
  {
    purpose: "AI_IMPROVEMENT",
    tag: "Art. 7º, I · Consentimento específico",
    icon: "sparkle",
    label: { "pt-BR": "Melhorar a IA do NOVA", "en-US": "Improve NOVA's AI", "es-ES": "Mejorar la IA de NOVA" },
    hint: {
      "pt-BR": "Usa seus projetos e conversas para treinar e avaliar a IA.",
      "en-US": "Uses your projects and chats to train and evaluate the AI.",
      "es-ES": "Usa tus proyectos y conversaciones para entrenar la IA.",
    },
  },
  {
    purpose: "PARTNER_SHARING",
    tag: "Art. 18, IX · Revogação",
    icon: "share",
    label: { "pt-BR": "Compartilhar com parceiros", "en-US": "Share with partners", "es-ES": "Compartir con socios" },
    hint: {
      "pt-BR": "Envia dados a parceiros do NOVA.",
      "en-US": "Sends data to NOVA partners.",
      "es-ES": "Envía datos a socios de NOVA.",
    },
  },
];

function PrivacySection({ settings, onSaved, t, lang }: { settings: Settings; onSaved: (s: Settings) => void; t: Dict; lang: Lang }) {
  const [busy, setBusy] = useState<string | null>(null);
  const feedback = useFeedback();
  const options: { value: Visibility; label: string; hint: string; icon: NovaIconName }[] = [
    { value: "PRIVATE", label: t.settings.onlyYou, hint: t.settings.onlyYouHint, icon: "lock" },
    { value: "COMMUNITY", label: t.settings.community, hint: t.settings.communityHint, icon: "userCircle" },
    { value: "PUBLIC", label: t.settings.public, hint: t.settings.publicHint, icon: "globe" },
  ];

  async function setVisibility(value: Visibility) {
    setBusy("visibility");
    const result = await callMe("/privacy", "PUT", { profileVisibility: value });
    setBusy(null);
    if (result.ok) onSaved(result.data);
    else feedback.fail(result.problem, t.common.error);
  }

  async function setConsent(purpose: ConsentPurpose, granted: boolean) {
    setBusy(purpose);
    const result = await callMe(`/consents/${purpose}`, "PUT", { granted });
    setBusy(null);
    if (result.ok) onSaved(result.data);
    else feedback.fail(result.problem, t.common.error);
  }

  const dateFmt = new Intl.DateTimeFormat(lang, { dateStyle: "short", timeStyle: "short" });

  return (
    <div className="flex flex-col gap-6">
      {feedback.message && <Alert tone={feedback.message.tone}>{feedback.message.text}</Alert>}
      <Card>
        <SectionTitle icon="eyeOff" title={t.settings.privacy} intro={t.settings.privacyIntro} />
        <fieldset>
          <legend className="mb-3 flex w-full flex-wrap items-center justify-between gap-2 text-sm font-semibold">
            {t.settings.whoSees}
            <LgpdTag>Art. 18, VII · Compartilhamento</LgpdTag>
          </legend>
          <div className="grid gap-3 md:grid-cols-3">
            {options.map((o) => {
              const selected = settings.privacy.profileVisibility === o.value;
              return (
                <label
                  key={o.value}
                  className={`flex cursor-pointer flex-col gap-1 rounded-xl border p-4 transition ${
                    selected ? "border-primary bg-primary/5 ring-2 ring-[var(--ring)]" : "border-border hover:bg-surface-2"
                  }`}
                >
                  <input
                    type="radio"
                    name="visibility"
                    className="sr-only"
                    checked={selected}
                    disabled={busy === "visibility"}
                    onChange={() => setVisibility(o.value)}
                  />
                  <NovaIcon name={o.icon} gradient={selected} />
                  <span className="text-sm font-semibold">{o.label}</span>
                  <span className="text-xs text-muted">{o.hint}</span>
                </label>
              );
            })}
          </div>
        </fieldset>
      </Card>
      <Card>
        <h3 className="font-bold">{t.settings.consentTitle}</h3>
        <p className="mb-4 mt-1 text-sm text-muted">{t.settings.consentIntro}</p>
        <ul className="divide-y divide-border">
          {CONSENTS.map((c) => {
            const state = settings.privacy.consents[c.purpose];
            return (
              <li key={c.purpose} className="flex flex-wrap items-center gap-4 py-4">
                <NovaIcon name={c.icon} size={22} className="text-muted" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold">{c.label[lang]}</p>
                  <p className="text-xs text-muted">{c.hint[lang]}</p>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <LgpdTag>{c.tag}</LgpdTag>
                    {state.updatedAt && (
                      <span className="text-[11px] text-muted">
                        {t.settings.lastChange}: {dateFmt.format(new Date(state.updatedAt))}
                      </span>
                    )}
                  </div>
                </div>
                <Toggle
                  id={`consent-${c.purpose}`}
                  label={c.label[lang]}
                  checked={state.granted}
                  disabled={busy === c.purpose}
                  onChange={(v) => setConsent(c.purpose, v)}
                />
              </li>
            );
          })}
        </ul>
      </Card>
    </div>
  );
}

/* ---------------- Aparência e idioma: preferências de interface (não são dados pessoais) ---------------- */

async function savePreferences(theme: Theme, language: Lang) {
  const res = await fetch("/api/preferences", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ theme, language }),
  });
  return res.ok ? ((await res.json()) as Settings) : null;
}

function applyTheme(theme: Theme) {
  const root = document.documentElement;
  root.classList.remove("dark", "theme-system");
  if (theme === "DARK") root.classList.add("dark");
  if (theme === "SYSTEM") root.classList.add("theme-system");
}

function AppearanceSection({ settings, onSaved, t }: { settings: Settings; onSaved: (s: Settings) => void; t: Dict }) {
  const [theme, setTheme] = useState<Theme>(settings.preferences.theme);
  const feedback = useFeedback();

  async function choose(next: Theme) {
    setTheme(next);
    applyTheme(next);
    const saved = await savePreferences(next, settings.preferences.language);
    if (saved) onSaved(saved);
    else feedback.fail({}, t.common.error);
  }

  const options: { value: Theme; label: string; icon: NovaIconName }[] = [
    { value: "SYSTEM", label: t.settings.themeSystem, icon: "layout" },
    { value: "LIGHT", label: t.settings.themeLight, icon: "sparkle" },
    { value: "DARK", label: t.settings.themeDark, icon: "moon" },
  ];

  return (
    <Card>
      <SectionTitle icon="moon" title={t.settings.appearance} intro={t.settings.appearanceIntro} />
      {feedback.message && <Alert tone={feedback.message.tone}>{feedback.message.text}</Alert>}
      <div className="mb-5 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-border p-4">
        <div>
          <p className="text-sm font-semibold">{t.settings.darkMode}</p>
          <p className="text-xs text-muted">{t.settings.darkModeHint}</p>
        </div>
        <Toggle id="dark-mode" label={t.settings.darkMode} checked={theme === "DARK"} onChange={(v) => choose(v ? "DARK" : "LIGHT")} />
      </div>
      <p className="mb-2 text-sm font-semibold">{t.settings.theme}</p>
      <div className="grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label={t.settings.theme}>
        {options.map((o) => (
          <button
            key={o.value}
            role="radio"
            aria-checked={theme === o.value}
            onClick={() => choose(o.value)}
            className={`flex items-center gap-2 rounded-xl border p-3 text-sm font-semibold ${
              theme === o.value ? "border-primary bg-primary/5 ring-2 ring-[var(--ring)]" : "border-border hover:bg-surface-2"
            }`}
          >
            <NovaIcon name={o.icon} gradient={theme === o.value} />
            {o.label}
          </button>
        ))}
      </div>
      <div className="mt-5">
        <Alert tone="info">{t.settings.modelingNote}</Alert>
      </div>
    </Card>
  );
}

function LanguageSection({ settings, onSaved, t }: { settings: Settings; onSaved: (s: Settings) => void; t: Dict }) {
  const router = useRouter();
  const [language, setLanguage] = useState<Lang>(settings.preferences.language);
  const [pending, setPending] = useState(false);

  async function choose(next: Lang) {
    setLanguage(next);
    setPending(true);
    const saved = await savePreferences(settings.preferences.theme, next);
    setPending(false);
    if (saved) {
      onSaved(saved);
      router.refresh();
    }
  }

  return (
    <Card>
      <SectionTitle icon="globe" title={t.settings.language} intro={t.settings.languageIntro} />
      <Field label={t.settings.language} htmlFor="language" hint={t.settings.languageHint}>
        <select
          id="language"
          value={language}
          disabled={pending}
          onChange={(e) => choose(e.target.value as Lang)}
          className="w-full max-w-xs rounded-lg border border-border bg-surface px-3 py-2 text-sm"
        >
          <option value="pt-BR">Português (Brasil)</option>
          <option value="en-US">English (US)</option>
          <option value="es-ES">Español</option>
        </select>
      </Field>
    </Card>
  );
}

/* ---------------- Seus dados: portabilidade (art. 18, V) e eliminação (art. 18, VI) ---------------- */

function DataSection({ settings, t }: { settings: Settings; t: Dict }) {
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const feedback = useFeedback();

  async function exportData() {
    setExporting(true);
    const res = await fetch("/api/backend/me/export");
    setExporting(false);
    if (!res.ok) {
      feedback.fail({}, t.common.error);
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "nova-meus-dados.json";
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="flex flex-col gap-6">
      {feedback.message && <Alert tone={feedback.message.tone}>{feedback.message.text}</Alert>}
      <Card>
        <SectionTitle icon="dataExport" title={t.settings.yourData} intro={t.settings.dataIntro} />
        <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-border p-4">
          <div>
            <p className="text-sm font-semibold">{t.settings.download}</p>
            <p className="text-xs text-muted">{t.settings.downloadHint}</p>
            <div className="mt-1">
              <LgpdTag>Art. 18, V · Portabilidade</LgpdTag>
            </div>
          </div>
          <Button variant="secondary" icon="download" onClick={exportData} disabled={exporting}>
            {exporting ? t.common.loading : t.settings.export}
          </Button>
        </div>
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-surface-2 p-4 text-sm">
          <span className="text-muted">{t.settings.terms}</span>
          <span className="font-semibold">
            v{settings.terms.version} · {new Date(settings.terms.acceptedAt).toLocaleDateString()}
          </span>
          <LgpdTag>Art. 18, I e II · Confirmação e acesso</LgpdTag>
        </div>
      </Card>
      <Card className="border-danger/40">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <p className="flex items-center gap-2 font-bold text-danger">
              <NovaIcon name="trash" size={18} />
              {t.settings.deleteAccount}
            </p>
            <p className="mt-1 text-xs text-muted">{t.settings.deleteHint}</p>
            <div className="mt-1">
              <LgpdTag>Art. 18, VI · Eliminação</LgpdTag>
            </div>
          </div>
          <Button variant="danger" icon="trash" onClick={() => setDeleteOpen(true)}>
            {t.settings.deleteAccount}
          </Button>
        </div>
      </Card>
      {deleteOpen && <DeleteDialog t={t} onClose={() => setDeleteOpen(false)} />}
    </div>
  );
}

function DeleteDialog({ t, onClose }: { t: Dict; onClose: () => void }) {
  const router = useRouter();
  const [form, setForm] = useState({ currentPassword: "", confirmation: "" });
  const [pending, setPending] = useState(false);
  const feedback = useFeedback();

  async function confirm(e: FormEvent) {
    e.preventDefault();
    setPending(true);
    const result = await callMe<null>("", "DELETE", form);
    if (result.ok) {
      await fetch("/api/auth/logout", { method: "POST" });
      router.replace("/");
      router.refresh();
      return;
    }
    setPending(false);
    feedback.fail(result.problem, t.common.error);
  }

  return (
    <Dialog title={t.settings.deleteConfirmTitle} onClose={onClose}>
      <form onSubmit={confirm} className="flex flex-col gap-4" noValidate>
        <Alert tone="danger">{t.settings.deleteConfirmText}</Alert>
        {feedback.message && <Alert tone={feedback.message.tone}>{feedback.message.text}</Alert>}
        <Field label={t.settings.currentPassword} htmlFor="deletePassword">
          <Input
            id="deletePassword"
            type="password"
            autoComplete="current-password"
            value={form.currentPassword}
            onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
          />
        </Field>
        <Field label={t.settings.typeDelete} htmlFor="deleteConfirm">
          <Input id="deleteConfirm" value={form.confirmation} onChange={(e) => setForm({ ...form, confirmation: e.target.value })} />
        </Field>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button type="submit" variant="danger" disabled={pending || form.confirmation !== "EXCLUIR" || !form.currentPassword}>
            {t.settings.deleteForever}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

type Session = { id: string; device: string; startedAt: string; lastActiveAt: string; current: boolean };

/** Sessões abertas da conta (segurança, LGPD art. 46): onde a conta está conectada e o botão de encerrar cada uma. */
function SessionsCard({ lastLoginAt, passwordChangedAt }: { lastLoginAt: string | null; passwordChangedAt: string | null }) {
  const [sessions, setSessions] = useState<Session[] | null>(null);
  const feedback = useFeedback();
  useEffect(() => {
    let alive = true;
    void callMe<Session[]>("/sessions", "GET").then((r) => {
      if (alive && r.ok) setSessions(r.data);
    });
    return () => {
      alive = false;
    };
  }, []);
  async function end(id: string) {
    const r = await callMe<null>(`/sessions/${id}`, "DELETE");
    if (r.ok) {
      setSessions((s) => (s ?? []).filter((x) => x.id !== id));
      feedback.ok("Sessão encerrada.");
    } else feedback.fail(r.problem, "Não foi possível encerrar.");
  }
  const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString("pt-BR") : "—");
  return (
    <Card>
      <SectionTitle icon="lock" title="Sessões e acessos" intro={`Último acesso: ${when(lastLoginAt)} · Senha trocada em: ${when(passwordChangedAt)}`} />
      {sessions === null ? <p className="text-sm text-muted">Carregando…</p> : (
        <ul className="flex flex-col gap-2">
          {sessions.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
              <span>
                <span className="font-semibold">{s.device}</span>{s.current && <span className="ml-2 text-xs font-semibold text-success">esta sessão</span>}
                <span className="block text-xs text-muted">Desde {when(s.startedAt)} · última atividade {when(s.lastActiveAt)}</span>
              </span>
              {!s.current && <Button variant="secondary" onClick={() => end(s.id)}>Encerrar</Button>}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
