import type { Metadata } from "next";
import Link from "next/link";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Card, PageHeader } from "@/components/ui/ui";
import { NAV, PHASES } from "@/lib/nav";
import { getPrefs } from "@/lib/server-prefs";
import { getCurrentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Painel" };

export default async function DashboardPage() {
  const [{ lang, t }, user] = await Promise.all([getPrefs(), getCurrentUser()]);
  const firstName = user?.name.split(" ")[0] ?? "";
  const items = NAV.flatMap((g) => g.items).filter((i) => !i.adminOnly && i.slug !== "painel");
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="home" title={`${t.dashboard.hello}, ${firstName}`} intro={t.dashboard.intro} />
      <div className="grid gap-6 lg:grid-cols-[1fr_1.4fr] [&>*]:min-w-0">
        <Card>
          <h2 className="mb-4 font-bold">{t.dashboard.phases}</h2>
          <ol className="flex flex-col gap-3">
            {PHASES.map((p) => (
              <li key={p.phase} className="flex items-start gap-3">
                <span
                  className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold ${
                    p.phase === 1 ? "nova-gradient-bar text-white" : "bg-surface-2 text-muted"
                  }`}
                >
                  {p.phase}
                </span>
                <span className={`text-sm ${p.phase === 1 ? "font-semibold" : "text-muted"}`}>{p.title[lang]}</span>
              </li>
            ))}
          </ol>
        </Card>
        <Card>
          <h2 className="mb-4 font-bold">{t.dashboard.quick}</h2>
          <ul className="grid gap-2 sm:grid-cols-2 [&>li]:min-w-0">
            {items.map((item) => (
              <li key={item.slug}>
                <Link href={item.href} className="flex items-center gap-3 rounded-xl border border-border p-3 hover:bg-surface-2">
                  <NovaIcon name={item.icon} size={22} gradient={item.ready} className={item.ready ? "" : "text-muted"} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{item.label[lang]}</span>
                    <span className="block truncate text-xs text-muted">{item.rf}</span>
                  </span>
                  {!item.ready && <span className="text-[10px] font-semibold text-muted">F{item.phase}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  );
}
