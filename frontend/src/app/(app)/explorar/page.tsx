import type { Metadata } from "next";
import Link from "next/link";
import { ProjectCardGrid } from "@/components/community/ProjectCardGrid";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { CatalogIcon } from "@/components/tech/CatalogIcon";
import { Alert, Card, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";
import type { CommunityCard } from "@/lib/community-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Explorar" };

export default async function ExplorePage({ searchParams }: PageProps<"/explorar">) {
  const params = await searchParams;
  const q = typeof params.q === "string" ? params.q.slice(0, 80) : "";
  const tech = typeof params.tech === "string" ? params.tech.slice(0, 40) : "";
  const query = new URLSearchParams();
  if (q) query.set("q", q);
  if (tech) query.set("tech", tech);
  const [list, loaded] = await Promise.all([api<CommunityCard[]>(`community/projects?${query}`), loadCatalog()]);
  if (!loaded) return <Alert tone="danger">Não foi possível carregar o catálogo.</Alert>;
  const c = loaded.catalog;
  const techs = [...c.backends, ...c.frontends, ...c.databases, ...c.connectors].filter((t) => !("status" in t) || t.status !== "planned");
  const href = (id: string) => {
    const next = new URLSearchParams();
    if (q) next.set("q", q);
    if (id) next.set("tech", id);
    const s = next.toString();
    return s ? `/explorar?${s}` : "/explorar";
  };
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="compass" title="Explorar" intro="Busque projetos da rede por nome, descrição ou tecnologia." />
      <form action="/explorar" className="mb-4 flex flex-wrap gap-2" role="search">
        {tech && <input type="hidden" name="tech" value={tech} />}
        <label className="relative min-w-0 flex-1">
          <span className="sr-only">Buscar projetos</span>
          <NovaIcon name="search" size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            name="q"
            defaultValue={q}
            placeholder="Buscar projetos…"
            className="w-full rounded-lg border border-border bg-surface py-2 pl-9 pr-3 text-sm focus:border-primary focus:outline-none focus:ring-4 focus:ring-[var(--ring)]"
          />
        </label>
        <button className="rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-text hover:brightness-110">Buscar</button>
      </form>
      <nav aria-label="Filtrar por tecnologia" className="mb-5 flex flex-wrap gap-2">
        <Link href={href("")} aria-current={!tech ? "true" : undefined} className={`rounded-full border px-3 py-1 text-xs font-semibold ${!tech ? "border-primary bg-primary/10 text-primary" : "border-border text-muted hover:bg-surface-2"}`}>
          Todas
        </Link>
        {techs.map((t) => (
          <Link
            key={t.id}
            href={href(t.id)}
            aria-current={tech === t.id ? "true" : undefined}
            className={`inline-flex items-center gap-1.5 rounded-full border py-1 pl-1 pr-3 text-xs font-semibold ${tech === t.id ? "border-primary bg-primary/10 text-primary" : "border-border text-muted hover:bg-surface-2"}`}
          >
            <CatalogIcon icons={loaded.icons} slug={t.icon} name={t.name} size={20} />
            {t.name}
          </Link>
        ))}
      </nav>
      {!list.ok ? (
        <Alert tone="danger">Não foi possível buscar.</Alert>
      ) : list.data.length === 0 ? (
        <Card className="py-10 text-center text-sm text-muted">Nenhum projeto encontrado{q ? ` para “${q}”` : ""}.</Card>
      ) : (
        <ProjectCardGrid cards={list.data} catalog={c} icons={loaded.icons} />
      )}
    </div>
  );
}
