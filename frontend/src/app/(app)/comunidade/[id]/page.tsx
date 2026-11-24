import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CommunityProject } from "@/components/community/CommunityProject";
import { loadCatalog } from "@/lib/catalog";
import type { FileEntry } from "@/lib/catalog-types";
import type { CommunityCard, CommunityComment } from "@/lib/community-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Projeto da rede" };

type FileContent = { path: string; size: number; binary: boolean; content: string | null };

export default async function CommunityProjectPage({ params }: PageProps<"/comunidade/[id]">) {
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/.test(id)) notFound();
  const base = `community/projects/${id}`;
  const [card, tree, comments, readme, loaded] = await Promise.all([
    api<CommunityCard>(base),
    api<FileEntry[]>(`${base}/tree`),
    api<CommunityComment[]>(`${base}/comments`),
    api<FileContent>(`${base}/file?path=README.md`),
    loadCatalog(),
  ]);
  if (!card.ok || !tree.ok) notFound();
  return (
    <CommunityProject
      apiBase={`/api/backend/${base}`}
      card={card.data}
      tree={tree.data}
      initialFile={readme.ok ? readme.data : null}
      initialComments={comments.ok ? comments.data : []}
      catalog={loaded?.catalog ?? null}
      icons={loaded?.icons ?? {}}
      interactive
    />
  );
}
