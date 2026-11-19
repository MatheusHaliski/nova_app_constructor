"use client";

import { useMemo, useState } from "react";
import { NovaIcon } from "@/components/icons/NovaIcon";
import type { Dict } from "@/lib/i18n";
import type { Tech, TechCategory } from "@/lib/techs";
import { TechIcon } from "./TechIcon";

const CATEGORY_LABEL: Record<TechCategory, string> = {
  linguagem: "Linguagens",
  backend: "Backend",
  frontend: "Frontend",
  mobile: "Mobile",
  banco: "Banco de dados",
  conector: "Conectores",
  api: "APIs e mensageria",
  ia: "IA externa",
  infra: "Infraestrutura",
  qualidade: "Qualidade",
};

/** Busca de tecnologias com ícones — o mesmo componente servirá aos formulários de stack e ao feed (Fases 3 e 4). */
export function TechCatalog({ techs, categories, t }: { techs: Tech[]; categories: TechCategory[]; t: Dict }) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<TechCategory | "all">("all");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return techs.filter(
      (tech) =>
        (category === "all" || tech.category === category) &&
        (!q || tech.name.toLowerCase().includes(q) || tech.description.toLowerCase().includes(q)),
    );
  }, [techs, query, category]);

  const roleLabel = { nova: t.techs.novaStack, opcao: t.techs.option, conector: t.techs.connector };

  return (
    <div className="flex flex-col gap-4">
      <div className="relative">
        <NovaIcon name="search" size={18} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t.techs.search}
          aria-label={t.techs.search}
          className="w-full rounded-xl border border-border bg-surface py-2.5 pl-10 pr-3 text-sm focus:border-primary focus:outline-none focus:ring-4 focus:ring-[var(--ring)]"
        />
      </div>
      <div className="flex flex-wrap gap-2" role="tablist">
        {(["all", ...categories] as const).map((c) => (
          <button
            key={c}
            role="tab"
            aria-selected={category === c}
            onClick={() => setCategory(c)}
            className={`rounded-full border px-3 py-1 text-xs font-semibold ${
              category === c ? "border-primary bg-primary text-primary-text" : "border-border bg-surface text-muted hover:text-text"
            }`}
          >
            {c === "all" ? t.techs.all : CATEGORY_LABEL[c]}
          </button>
        ))}
      </div>
      {filtered.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted">{t.techs.empty}</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((tech) => (
            <li key={tech.id} className="flex items-start gap-3 rounded-xl border border-border bg-surface p-3">
              <TechIcon tech={tech} size={40} />
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">{tech.name}</p>
                <p className="text-xs text-muted">{tech.description}</p>
                <span
                  className={`mt-1 inline-block rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                    tech.role === "nova"
                      ? "bg-primary/10 text-primary"
                      : tech.role === "conector"
                        ? "bg-warning/10 text-warning"
                        : "bg-surface-2 text-muted"
                  }`}
                >
                  {roleLabel[tech.role]}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
