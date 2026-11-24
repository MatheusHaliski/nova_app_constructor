import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ProjectWorkspace } from "@/components/projects/ProjectWorkspace";
import { loadCatalog } from "@/lib/catalog";
import type { FileEntry, ProjectSummary, VersionSummary } from "@/lib/catalog-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Projeto" };

export default async function ProjectPage({ params, searchParams }: PageProps<"/projetos/[id]">) {
  const { id } = await params;
  const share = (await searchParams).aba === "compartilhar";
  if (!/^[0-9a-f-]{36}$/.test(id)) notFound();
  const [project, tree, versions, readme, loaded] = await Promise.all([
    api<ProjectSummary>(`projects/${id}`),
    api<FileEntry[]>(`projects/${id}/tree`),
    api<VersionSummary[]>(`projects/${id}/versions`),
    api<{ content: string | null }>(`projects/${id}/file?path=README.md`),
    loadCatalog(),
  ]);
  if (!project.ok || !tree.ok || !versions.ok || !loaded) notFound();
  return (
    <ProjectWorkspace
      project={project.data}
      initialTree={tree.data}
      initialVersions={versions.data}
      readme={readme.ok ? readme.data.content : null}
      catalog={loaded.catalog}
      icons={loaded.icons}
      initialTab={share ? "share" : "overview"}
    />
  );
}
