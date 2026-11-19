import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { NovaIcon, type NovaIconName } from "@/components/icons/NovaIcon";
import { SettingsSection } from "@/components/settings/SettingsSections";
import { SECTIONS, type Section, type Settings } from "@/components/settings/types";
import { PageHeader } from "@/components/ui/ui";
import { getPrefs } from "@/lib/server-prefs";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Configurações" };

const ICONS: Record<Section, NovaIconName> = {
  conta: "user",
  privacidade: "eyeOff",
  aparencia: "moon",
  idioma: "globe",
  "seus-dados": "dataExport",
};

export default async function SettingsPage({ params }: PageProps<"/configuracoes/[secao]">) {
  const { secao } = await params;
  if (!SECTIONS.includes(secao as Section)) notFound();
  const section = secao as Section;
  const [{ lang, t }, result] = await Promise.all([getPrefs(), api<Settings>("me/settings")]);
  if (!result.ok) redirect("/login");

  const labels: Record<Section, string> = {
    conta: t.settings.account,
    privacidade: t.settings.privacy,
    aparencia: t.settings.appearance,
    idioma: t.settings.language,
    "seus-dados": t.settings.yourData,
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="sliders" title={t.settings.title} />
      <div className="grid gap-6 lg:grid-cols-[220px_1fr]">
        <nav aria-label={t.settings.title} className="flex gap-1 overflow-x-auto lg:flex-col">
          {SECTIONS.map((s) => {
            const active = s === section;
            return (
              <Link
                key={s}
                href={`/configuracoes/${s}`}
                aria-current={active ? "page" : undefined}
                className={`flex shrink-0 items-center gap-3 rounded-lg px-3 py-2 text-sm ${
                  active ? "bg-primary/10 font-semibold" : "text-muted hover:bg-surface-2 hover:text-text"
                }`}
              >
                <NovaIcon name={ICONS[s]} size={18} gradient={active} />
                {labels[s]}
              </Link>
            );
          })}
        </nav>
        <SettingsSection key={section} section={section} initial={result.data} t={t} lang={lang} />
      </div>
    </div>
  );
}
