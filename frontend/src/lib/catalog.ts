import "server-only";
import type { Catalog } from "./catalog-types";
import { api } from "./session";
import { iconsFor } from "./techs";

/** Catálogo do backend e os ícones das tecnologias que ele cita. */
export async function loadCatalog() {
  const result = await api<Catalog>("catalog");
  if (!result.ok) return null;
  const c = result.data;
  const slugs = new Set<string>();
  for (const list of [c.languages, c.backends, c.frontends, c.databases, c.connectors, c.aiProviders]) {
    for (const item of list) slugs.add(item.icon);
  }
  for (const stack of [...c.backends, ...c.frontends]) {
    for (const tool of stack.buildTools) slugs.add(tool.icon);
  }
  return { catalog: c, icons: iconsFor(slugs) };
}
