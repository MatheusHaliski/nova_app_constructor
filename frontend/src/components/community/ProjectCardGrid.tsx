import Link from "next/link";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { formatDate } from "@/components/projects/format";
import { StackBadges } from "@/components/projects/StackBadges";
import type { Catalog } from "@/lib/catalog-types";
import { LICENSES, type CommunityCard } from "@/lib/community-types";
import type { IconData } from "@/lib/techs";

/** Cards compactos dos projetos da rede: autor, tecnologias (ícones), licença, estrelas, forks e comentários. */
export function ProjectCardGrid({ cards, catalog, icons }: { cards: CommunityCard[]; catalog: Catalog; icons: Record<string, IconData> }) {
  return (
    <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {cards.map((c) => (
        <li key={c.id} className="min-w-0">
          <Link href={`/comunidade/${c.id}`} className="flex h-full flex-col gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm transition hover:border-primary">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <h2 className="truncate font-bold">{c.name}</h2>
                <p className="truncate text-xs text-muted">
                  @{c.author}
                  {c.mine && " · seu projeto"}
                </p>
              </div>
              <span className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-semibold text-muted">{c.type === "APP" ? "App" : "Web App"}</span>
            </div>
            <p className="line-clamp-2 min-h-8 text-xs text-muted">{c.description ?? "Sem descrição."}</p>
            <StackBadges spec={c.spec} catalog={catalog} icons={icons} />
            <p className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted">
              <span className="inline-flex items-center gap-1" title="Estrelas">
                <NovaIcon name="star" size={12} gradient={c.starredByMe} /> {c.stars}
              </span>
              <span className="inline-flex items-center gap-1" title="Forks">
                <NovaIcon name="git" size={12} /> {c.forks}
              </span>
              <span title="Comentários">{c.comments} comentários</span>
              {c.license && <span>{LICENSES[c.license].label}</span>}
              {c.publishedAt && <span>{formatDate(c.publishedAt)}</span>}
            </p>
          </Link>
        </li>
      ))}
    </ul>
  );
}
