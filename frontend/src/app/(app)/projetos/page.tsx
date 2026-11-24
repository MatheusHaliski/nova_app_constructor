import type { Metadata } from "next";
import Link from "next/link";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { formatBytes, formatDate } from "@/components/projects/format";
import { VisibilityBadge } from "@/components/community/VisibilityBadge";
import { StackBadges } from "@/components/projects/StackBadges";
import { Alert, Card, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";
import type { ProjectSummary } from "@/lib/catalog-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Projetos" };

const TABS = [
  { status: "ACTIVE", label: "Ativos" },
  { status: "ARCHIVED", label: "Arquivados" },
  { status: "TRASHED", label: "Lixeira" },
] as const;

export default async function ProjectsPage({ searchParams }: PageProps<"/projetos">) {
  const { status: raw } = await searchParams;
  const status = TABS.find((t) => t.status === raw)?.status ?? "ACTIVE";
  const [list, usage, loaded] = await Promise.all([
    api<ProjectSummary[]>(`projects?status=${status}`),
    api<{ projects: number; storageBytes: number; storageLimitBytes: number }>("projects/usage"),
    loadCatalog(),
  ]);
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="folder" title="Projetos" intro="Seus projetos salvos. Todos nascem privados; você escolhe o que compartilhar em cada um.">
        <Link href="/novo-app" className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-text hover:brightness-110">
          <NovaIcon name="sparkle" size={16} />
          Novo app
        </Link>
      </PageHeader>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <nav className="flex gap-1" aria-label="Situação">
          {TABS.map((t) => (
            <Link
              key={t.status}
              href={`/projetos?status=${t.status}`}
              aria-current={status === t.status ? "page" : undefined}
              className={`rounded-full px-3 py-1 text-sm font-semibold ${status === t.status ? "bg-primary text-primary-text" : "text-muted hover:bg-surface-2"}`}
            >
              {t.label}
            </Link>
          ))}
        </nav>
        {usage.ok && (
          <p className="text-xs text-muted">
            {usage.data.projects} projetos · {formatBytes(usage.data.storageBytes)} de {formatBytes(usage.data.storageLimitBytes)}
          </p>
        )}
      </div>
      {!list.ok || !loaded ? (
        <Alert tone="danger">Não foi possível carregar os projetos.</Alert>
      ) : list.data.length === 0 ? (
        <Card className="flex flex-col items-center gap-3 py-12 text-center text-muted">
          <NovaIcon name="folder" size={40} gradient />
          <p>{status === "ACTIVE" ? "Você ainda não tem projetos." : "Nada por aqui."}</p>
          {status === "ACTIVE" && (
            <Link href="/novo-app" className="font-semibold text-primary">
              Criar o primeiro app →
            </Link>
          )}
        </Card>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {list.data.map((p) => (
            <li key={p.id} className="min-w-0">
              <Link href={`/projetos/${p.id}`} className="flex h-full flex-col gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm transition hover:border-primary">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="truncate font-bold">{p.name}</h2>
                  <span className="shrink-0 rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-semibold text-muted">{p.type === "APP" ? "App" : "Web App"}</span>
                </div>
                <p className="line-clamp-2 min-h-8 text-xs text-muted">{p.description ?? "Sem descrição."}</p>
                <StackBadges spec={p.spec} catalog={loaded.catalog} icons={loaded.icons} />
                <p className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-muted">
                  <VisibilityBadge visibility={p.visibility} />
                  <span>v{p.currentVersion}</span>
                  <span>{p.fileCount} arquivos</span>
                  <span>{formatBytes(p.sizeBytes)}</span>
                  <span>{formatDate(p.updatedAt)}</span>
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
