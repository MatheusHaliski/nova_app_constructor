import type { Metadata } from "next";
import Link from "next/link";
import { ProjectCardGrid } from "@/components/community/ProjectCardGrid";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Alert, Card, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";
import type { CommunityCard } from "@/lib/community-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Feed" };

export default async function FeedPage() {
  const [list, loaded] = await Promise.all([api<CommunityCard[]>("community/projects"), loadCatalog()]);
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="feed" title="Feed" intro="Projetos publicados na rede NOVA, dos mais recentes para os mais antigos.">
        <Link href="/explorar" className="inline-flex items-center gap-2 rounded-lg border border-border bg-surface px-4 py-2 text-sm font-semibold hover:bg-surface-2">
          <NovaIcon name="compass" size={16} /> Explorar por tecnologia
        </Link>
      </PageHeader>
      {!list.ok || !loaded ? (
        <Alert tone="danger">Não foi possível carregar o feed.</Alert>
      ) : list.data.length === 0 ? (
        <Card className="flex flex-col items-center gap-3 py-12 text-center text-muted">
          <NovaIcon name="feed" size={40} gradient />
          <p>Ninguém publicou ainda. Abra um projeto seu e use a aba Compartilhar.</p>
          <Link href="/projetos" className="font-semibold text-primary">
            Ir para meus projetos →
          </Link>
        </Card>
      ) : (
        <ProjectCardGrid cards={list.data} catalog={loaded.catalog} icons={loaded.icons} />
      )}
    </div>
  );
}
