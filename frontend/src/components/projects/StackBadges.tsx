import { CatalogIcon } from "@/components/tech/CatalogIcon";
import type { Catalog, ProjectSpec } from "@/lib/catalog-types";
import type { IconData } from "@/lib/techs";

/** Selos das tecnologias de um projeto (card da lista, página do projeto e, depois, o feed). */
export function StackBadges({ spec, catalog, icons, size = 28 }: { spec: ProjectSpec; catalog: Catalog; icons: Record<string, IconData>; size?: number }) {
  const items = [
    catalog.backends.find((b) => b.id === spec.backend?.id),
    catalog.languages.find((l) => l.id === spec.backend?.language),
    catalog.frontends.find((f) => f.id === spec.frontend?.id),
    catalog.databases.find((d) => d.id === spec.database?.id),
    ...(spec.connectors ?? []).map((c) => catalog.connectors.find((x) => x.id === c)),
    catalog.aiProviders.find((a) => a.id === spec.ai?.provider),
  ].filter((x): x is NonNullable<typeof x> => Boolean(x));
  return (
    <span className="flex flex-wrap gap-1.5">
      {items.map((item) => (
        <CatalogIcon key={item.id} icons={icons} slug={item.icon} name={item.name} size={size} />
      ))}
    </span>
  );
}
