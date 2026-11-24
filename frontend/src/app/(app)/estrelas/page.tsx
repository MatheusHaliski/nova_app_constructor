import type { Metadata } from "next";
import Link from "next/link";
import { ProjectCardGrid } from "@/components/community/ProjectCardGrid";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Alert, Card, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";
import type { CommunityCard } from "@/lib/community-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Estrelas" };

export default async function StarsPage() {
  const [list, loaded] = await Promise.all([api<CommunityCard[]>("community/starred"), loadCatalog()]);
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="star" title="Estrelas" intro="Projetos da rede que você favoritou. Seus forks ficam em Projetos, com a origem indicada." />
      {!list.ok || !loaded ? (
        <Alert tone="danger">Não foi possível carregar.</Alert>
      ) : list.data.length === 0 ? (
        <Card className="flex flex-col items-center gap-3 py-12 text-center text-muted">
          <NovaIcon name="star" size={40} gradient />
          <p>Você ainda não favoritou nenhum projeto.</p>
          <Link href="/explorar" className="font-semibold text-primary">
            Explorar a rede →
          </Link>
        </Card>
      ) : (
        <ProjectCardGrid cards={list.data} catalog={loaded.catalog} icons={loaded.icons} />
      )}
    </div>
  );
}
