import type { Metadata } from "next";
import Link from "next/link";
import { ReportsTable } from "@/components/community/ReportsTable";
import { Alert, PageHeader } from "@/components/ui/ui";
import type { Report } from "@/lib/community-types";
import { requireAdmin } from "@/lib/require-admin";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Moderação" };

export default async function ModerationPage({ searchParams }: PageProps<"/admin/moderacao">) {
  await requireAdmin();
  const resolved = (await searchParams).status === "RESOLVED";
  const result = await api<Report[]>(`admin/reports?status=${resolved ? "RESOLVED" : "OPEN"}`);
  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader icon="shield" title="Moderação" intro="Denúncias de projetos e comentários da rede. Toda decisão avisa a pessoa afetada." />
      <nav className="mb-4 flex gap-1" aria-label="Situação">
        {[
          { href: "/admin/moderacao", label: "Abertas", on: !resolved },
          { href: "/admin/moderacao?status=RESOLVED", label: "Resolvidas", on: resolved },
        ].map((t) => (
          <Link key={t.href} href={t.href} aria-current={t.on ? "page" : undefined} className={`rounded-full px-3 py-1 text-sm font-semibold ${t.on ? "bg-primary text-primary-text" : "text-muted hover:bg-surface-2"}`}>
            {t.label}
          </Link>
        ))}
      </nav>
      {result.ok ? <ReportsTable key={String(resolved)} initial={result.data} resolved={resolved} /> : <Alert tone="danger">{result.problem.detail ?? "Não foi possível carregar."}</Alert>}
    </div>
  );
}
