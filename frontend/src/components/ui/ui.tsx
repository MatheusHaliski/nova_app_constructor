import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from "react";
import { NovaIcon, type NovaIconName } from "@/components/icons/NovaIcon";

/* Componentes básicos com os tokens de tema do NOVA. */

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

const VARIANTS: Record<ButtonVariant, string> = {
  primary: "bg-primary text-primary-text hover:brightness-110",
  secondary: "border border-border bg-surface text-text hover:bg-surface-2",
  ghost: "text-text hover:bg-surface-2",
  danger: "bg-danger text-white hover:brightness-110",
};

export function Button({
  variant = "primary",
  icon,
  className = "",
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; icon?: NovaIconName }) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-60 ${VARIANTS[variant]} ${className}`}
      {...props}
    >
      {icon && <NovaIcon name={icon} size={16} />}
      {children}
    </button>
  );
}

export function Field({
  label,
  hint,
  error,
  tag,
  children,
  htmlFor,
}: {
  label: string;
  hint?: string;
  error?: string;
  tag?: ReactNode;
  children: ReactNode;
  htmlFor: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <label htmlFor={htmlFor} className="text-sm font-semibold text-text">
          {label}
        </label>
        {tag}
      </div>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} className="text-xs font-medium text-danger">
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-muted">{hint}</p>
      )}
    </div>
  );
}

const inputClass =
  "w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text placeholder:text-muted focus:border-primary focus:outline-none focus:ring-4 focus:ring-[var(--ring)] disabled:opacity-60";

export function Input({ invalid, className = "", ...props }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      className={`${inputClass} ${invalid ? "border-danger" : ""} ${className}`}
      aria-invalid={invalid || undefined}
      aria-describedby={invalid && props.id ? `${props.id}-error` : undefined}
      {...props}
    />
  );
}

export function Textarea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={`${inputClass} min-h-24 resize-y ${className}`} {...props} />;
}

/** Etiqueta do artigo da LGPD que o controle cumpre (padrão da aula de configurações de perfil). */
export function LgpdTag({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-primary/30 bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
      <NovaIcon name="shield" size={12} />
      {children}
    </span>
  );
}

type AlertTone = "info" | "success" | "warning" | "danger";
const TONES: Record<AlertTone, { cls: string; icon: NovaIconName }> = {
  info: { cls: "border-primary/30 bg-primary/10 text-text", icon: "info" },
  success: { cls: "border-success/30 bg-success/10 text-text", icon: "check" },
  warning: { cls: "border-warning/40 bg-warning/10 text-text", icon: "alert" },
  danger: { cls: "border-danger/40 bg-danger/10 text-text", icon: "alert" },
};

export function Alert({ tone = "info", children }: { tone?: AlertTone; children: ReactNode }) {
  const t = TONES[tone];
  return (
    <div role={tone === "danger" ? "alert" : "status"} className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-sm ${t.cls}`}>
      <NovaIcon name={t.icon} size={16} className="mt-0.5 shrink-0" />
      <div>{children}</div>
    </div>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-2xl border border-border bg-surface p-5 shadow-sm ${className}`}>{children}</section>;
}

export function PageHeader({ icon, title, intro, children }: { icon: NovaIconName; title: string; intro?: string; children?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="flex min-w-0 items-start gap-3">
        <span className="mt-1 shrink-0 rounded-xl border border-border bg-surface p-2">
          <NovaIcon name={icon} size={22} gradient />
        </span>
        <div className="min-w-0">
          <h1 className="text-2xl font-bold tracking-tight break-words">{title}</h1>
          {intro && <p className="mt-1 max-w-2xl text-sm text-muted">{intro}</p>}
        </div>
      </div>
      {children}
    </header>
  );
}

/** Interruptor acessível (role=switch). */
export function Toggle({
  checked,
  onChange,
  label,
  disabled,
  id,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
  disabled?: boolean;
  id: string;
}) {
  return (
    <button
      id={id}
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition disabled:opacity-60 ${
        checked ? "bg-primary" : "bg-border"
      }`}
    >
      <span className={`inline-block h-5 w-5 rounded-full bg-white shadow transition ${checked ? "translate-x-5" : "translate-x-0.5"}`} />
    </button>
  );
}
