import { notFound } from "next/navigation";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Card } from "@/components/ui/ui";
import { findNavItem, PHASES, TRELLO_BOARD_URL } from "@/lib/nav";
import { getPrefs } from "@/lib/server-prefs";

/** Página dos itens da barra lateral que chegam em fases futuras: mostra o RF e a fase prevista. */
export default async function SoonPage({ params }: PageProps<"/em-breve/[slug]">) {
  const { slug } = await params;
  const item = findNavItem(slug);
  if (!item) notFound();
  const { lang, t } = await getPrefs();
  const phase = PHASES.find((p) => p.phase === item.phase);
  return (
    <div className="mx-auto max-w-2xl">
      <Card className="flex flex-col items-center gap-4 py-10 text-center">
        <span className="rounded-2xl border border-border p-4">
          <NovaIcon name={item.icon} size={40} gradient />
        </span>
        <h1 className="text-2xl font-bold">{item.label[lang]}</h1>
        <p className="max-w-md text-muted">{item.description[lang]}</p>
        <span className="inline-flex items-center gap-2 rounded-full bg-warning/10 px-3 py-1 text-xs font-semibold text-warning">
          <NovaIcon name="construction" size={14} />
          {t.soon.title}
        </span>
        <dl className="grid w-full max-w-md gap-2 text-left text-sm">
          <div className="flex justify-between gap-4 rounded-lg bg-surface-2 px-3 py-2">
            <dt className="text-muted">{t.soon.requirement}</dt>
            <dd className="text-right font-semibold">{item.rf}</dd>
          </div>
          <div className="flex justify-between gap-4 rounded-lg bg-surface-2 px-3 py-2">
            <dt className="text-muted">{t.soon.phase}</dt>
            <dd className="text-right font-semibold">
              {item.phase} · {phase?.title[lang]}
            </dd>
          </div>
        </dl>
        <p className="text-sm text-muted">{t.soon.text}</p>
        <a href={TRELLO_BOARD_URL} target="_blank" rel="noreferrer" className="text-sm font-semibold text-primary">
          {t.soon.trello} ↗
        </a>
      </Card>
    </div>
  );
}
