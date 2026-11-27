import type { Metadata } from "next";
import Link from "next/link";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Card, PageHeader } from "@/components/ui/ui";
import { NAV } from "@/lib/nav";
import { getPrefs } from "@/lib/server-prefs";
import { api, getCurrentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Painel" };

type Dashboard = {
  recent: { id: string; name: string; visibility: string; version: number; lastActivity: string; openProposals: number; gaps: number; readiness: number; nextStep: string | null }[];
  projects: number;
  unreadNotifications: number;
  openSuggestions: number;
  updates: { projectId: string; project: string; technology: string; current: string; available: string }[];
};

/** Painel inicial que orienta: continue de onde parou, o que falta em cada projeto e o que mudou nas tecnologias. */
export default async function DashboardPage() {
  const [{ lang, t }, user, data] = await Promise.all([getPrefs(), getCurrentUser(), api<Dashboard>("me/dashboard")]);
  const firstName = user?.name.split(" ")[0] ?? "";
  const shortcuts = NAV.flatMap((g) => g.items).filter((i) => i.ready && !i.adminOnly && i.slug !== "painel").slice(0, 8);
  const d = data.ok ? data.data : null;
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="home" title={`${t.dashboard.hello}, ${firstName}`} intro="Continue de onde parou e veja o que falta em cada projeto." />
      {d && (
        <div className="mb-5 grid gap-3 sm:grid-cols-3">
          {[
            { label: "Projetos ativos", value: d.projects, href: "/projetos", icon: "folder" as const },
            { label: "Notificações não lidas", value: d.unreadNotifications, href: "/notificacoes", icon: "bell" as const },
            { label: "Sugestões da rede para revisar", value: d.openSuggestions, href: "/projetos", icon: "share" as const },
          ].map((c) => (
            <Link key={c.label} href={c.href} className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-4 hover:border-primary">
              <NovaIcon name={c.icon} size={26} gradient />
              <span><span className="block text-2xl font-bold">{c.value}</span><span className="text-xs text-muted">{c.label}</span></span>
            </Link>
          ))}
        </div>
      )}
      <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr] [&>*]:min-w-0">
        <Card>
          <h2 className="mb-3 font-bold">Continue de onde parou</h2>
          {!d || d.recent.length === 0 ? (
            <div className="flex flex-col items-start gap-2 text-sm text-muted">
              <p>Você ainda não tem projetos.</p>
              <Link href="/novo-app" className="font-semibold text-primary">Criar o primeiro app →</Link>
            </div>
          ) : (
            <ul className="flex flex-col gap-2">
              {d.recent.map((p) => (
                <li key={p.id}>
                  <Link href={`/projetos/${p.id}`} className="block rounded-xl border border-border p-3 hover:border-primary">
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <span className="font-semibold">{p.name} <span className="text-xs font-normal text-muted">v{p.version}</span></span>
                      <span className="text-xs text-muted">{new Date(p.lastActivity).toLocaleDateString(lang)}</span>
                    </div>
                    <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-surface-2" aria-hidden="true">
                      <div className="nova-gradient-bar h-full" style={{ width: `${p.readiness}%` }} />
                    </div>
                    <p className="mt-1 text-xs text-muted">
                      {p.readiness}% do caminho até publicar · {p.openProposals} melhorias pendentes{p.gaps ? ` (${p.gaps} lacunas)` : ""}
                    </p>
                    {p.nextStep && <p className="mt-1 text-xs font-semibold text-primary">Próximo passo: {p.nextStep}</p>}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <div className="flex flex-col gap-6">
          <Card>
            <h2 className="mb-3 font-bold">Atualizações de tecnologia</h2>
            {!d || d.updates.length === 0 ? <p className="text-sm text-muted">Seus projetos usam as versões mais novas do catálogo.</p> : (
              <ul className="flex flex-col gap-2 text-sm">
                {d.updates.map((u) => (
                  <li key={u.projectId + u.technology}>
                    <Link href={`/projetos/${u.projectId}`} className="hover:underline"><strong>{u.technology}</strong> {u.current} → {u.available}</Link>
                    <span className="block text-xs text-muted">em {u.project}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card>
            <h2 className="mb-3 font-bold">{t.dashboard.quick}</h2>
            <ul className="grid gap-2 sm:grid-cols-2 [&>li]:min-w-0">
              {shortcuts.map((item) => (
                <li key={item.slug}>
                  <Link href={item.href} className="flex items-center gap-2 rounded-lg border border-border p-2 text-sm hover:bg-surface-2">
                    <NovaIcon name={item.icon} size={18} gradient />
                    <span className="truncate">{item.label[lang]}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      </div>
    </div>
  );
}
