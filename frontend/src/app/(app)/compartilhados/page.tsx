import type { Metadata } from "next";
import Link from "next/link";
import { VisibilityBadge } from "@/components/community/VisibilityBadge";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { formatDate } from "@/components/projects/format";
import { StackBadges } from "@/components/projects/StackBadges";
import { Alert, Card, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";
import type { ProjectSummary } from "@/lib/catalog-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Compartilhados" };

export default async function SharedPage() {
  const [list, loaded] = await Promise.all([api<ProjectSummary[]>("projects?status=ACTIVE"), loadCatalog()]);
  const shared = list.ok ? list.data.filter((p) => p.visibility !== "PRIVATE") : [];
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="share" title="Compartilhados" intro="Seus projetos que outras pessoas podem ver. Para mudar, abra o projeto e use a aba Compartilhar." />
      {!list.ok || !loaded ? (
        <Alert tone="danger">Não foi possível carregar.</Alert>
      ) : shared.length === 0 ? (
        <Card className="flex flex-col items-center gap-3 py-12 text-center text-muted">
          <NovaIcon name="lock" size={40} gradient />
          <p>Todos os seus projetos estão privados.</p>
          <Link href="/projetos" className="font-semibold text-primary">
            Ir para meus projetos →
          </Link>
        </Card>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {shared.map((p) => (
            <li key={p.id} className="min-w-0">
              <Link href={`/projetos/${p.id}?aba=compartilhar`} className="flex h-full flex-col gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm transition hover:border-primary">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="truncate font-bold">{p.name}</h2>
                  <VisibilityBadge visibility={p.visibility} />
                </div>
                <p className="line-clamp-2 min-h-8 text-xs text-muted">{p.description ?? "Sem descrição."}</p>
                <StackBadges spec={p.spec} catalog={loaded.catalog} icons={loaded.icons} />
                <p className="mt-auto text-[11px] text-muted">Atualizado em {formatDate(p.updatedAt)}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
