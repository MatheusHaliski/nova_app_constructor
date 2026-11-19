"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field, Input } from "@/components/ui/ui";
import type { Dict } from "@/lib/i18n";

type Problem = { title?: string; detail?: string; errors?: Record<string, string> };

/** Só aceita destinos internos depois do login (evita redirecionamento aberto). */
function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/painel";
}

export function LoginForm({ t }: { t: Dict }) {
  const router = useRouter();
  const params = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setPending(true);
    setError(null);
    const form = new FormData(e.currentTarget);
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: form.get("email"), password: form.get("password") }),
    });
    if (res.ok) {
      router.replace(safeNext(params.get("next")));
      router.refresh();
      return;
    }
    const problem = (await res.json().catch(() => ({}))) as Problem;
    setError(problem.detail ?? t.common.error);
    setPending(false);
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
      {error && <Alert tone="danger">{error}</Alert>}
      <Field label={t.auth.email} htmlFor="email">
        <Input id="email" name="email" type="email" autoComplete="email" required />
      </Field>
      <Field label={t.auth.password} htmlFor="password">
        <Input id="password" name="password" type="password" autoComplete="current-password" required />
      </Field>
      <Button type="submit" disabled={pending} className="mt-2 w-full">
        {pending ? t.common.loading : t.auth.login}
      </Button>
      <p className="text-center text-sm text-muted">
        {t.auth.noAccount}{" "}
        <Link href="/cadastro" className="font-semibold text-primary">
          {t.auth.createAccount}
        </Link>
      </p>
    </form>
  );
}

export function RegisterForm({ t }: { t: Dict }) {
  const router = useRouter();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setPending(true);
    setError(null);
    setErrors({});
    const form = new FormData(e.currentTarget);
    const res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: form.get("name"),
        username: String(form.get("username") ?? "").toLowerCase(),
        email: form.get("email"),
        password: form.get("password"),
        birthDate: form.get("birthDate") || null,
        acceptTerms: form.get("acceptTerms") === "on",
      }),
    });
    if (res.ok) {
      router.replace("/painel");
      router.refresh();
      return;
    }
    const problem = (await res.json().catch(() => ({}))) as Problem;
    setErrors(problem.errors ?? {});
    setError(problem.detail ?? t.common.error);
    setPending(false);
  }

  const err = (k: string) => errors[k];

  /** Ao corrigir um campo, a mensagem de erro dele some. */
  function clearFieldError(e: FormEvent<HTMLFormElement>) {
    const name = (e.target as HTMLInputElement).name;
    if (name && errors[name]) {
      setErrors((current) => {
        const next = { ...current };
        delete next[name];
        return next;
      });
    }
  }

  return (
    <form onSubmit={onSubmit} onInput={clearFieldError} className="flex flex-col gap-4" noValidate>
      {error && <Alert tone="danger">{error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t.auth.name} htmlFor="name" error={err("name")}>
          <Input id="name" name="name" autoComplete="name" required invalid={!!err("name")} />
        </Field>
        <Field label={t.auth.username} htmlFor="username" hint={t.auth.usernameHint} error={err("username")}>
          <Input id="username" name="username" autoComplete="username" required invalid={!!err("username")} />
        </Field>
      </div>
      <Field label={t.auth.email} htmlFor="email" error={err("email")}>
        <Input id="email" name="email" type="email" autoComplete="email" required invalid={!!err("email")} />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t.auth.password} htmlFor="password" hint={t.auth.passwordHint} error={err("password")}>
          <Input id="password" name="password" type="password" autoComplete="new-password" required invalid={!!err("password")} />
        </Field>
        <Field label={t.auth.birthDate} htmlFor="birthDate" hint={t.auth.birthDateHint} error={err("birthDate")}>
          <Input id="birthDate" name="birthDate" type="date" required invalid={!!err("birthDate")} />
        </Field>
      </div>
      {/* Aceite como ação afirmativa: o checkbox começa desmarcado (LGPD, art. 8º). */}
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" name="acceptTerms" className="mt-1 h-4 w-4 accent-[var(--primary)]" />
        <span>
          {t.auth.acceptTerms}
          {err("acceptTerms") && <span className="block text-xs font-medium text-danger">{err("acceptTerms")}</span>}
        </span>
      </label>
      <Alert tone="info">{t.auth.privacyNote}</Alert>
      <Button type="submit" disabled={pending} className="w-full">
        {pending ? t.common.loading : t.auth.register}
      </Button>
      <p className="text-center text-sm text-muted">
        {t.auth.hasAccount}{" "}
        <Link href="/login" className="font-semibold text-primary">
          {t.auth.login}
        </Link>
      </p>
    </form>
  );
}
