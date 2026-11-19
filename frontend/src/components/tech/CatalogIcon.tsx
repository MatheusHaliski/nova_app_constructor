import type { IconData } from "@/lib/techs";
import { TechIcon } from "./TechIcon";

/** Ícone de um item do catálogo (pelo slug); sem logo livre, mostra o monograma no estilo NOVA. */
export function CatalogIcon({ icons, slug, name, size = 32 }: { icons: Record<string, IconData>; slug: string; name: string; size?: number }) {
  return <TechIcon tech={{ name, icon: icons[slug] }} size={size} />;
}
