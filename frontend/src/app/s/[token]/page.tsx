import type { Metadata } from "next";
import Link from "next/link";
import { NovaLogo } from "@/components/brand/NovaLogo";
import { CommunityProject } from "@/components/community/CommunityProject";
import { Card } from "@/components/ui/ui";
import type { FileEntry } from "@/lib/catalog-types";
import type { CommunityCard } from "@/lib/community-types";
import { backendFetch } from "@/lib/session";

/** Projeto aberto por link de compartilhamento: não exige conta e não deve ser indexado nem vazar o endereço. */
export const metadata: Metadata = { title: "Projeto compartilhado", robots: { index: false, follow: false }, referrer: "no-referrer" };

type FileContent = { path: string; size: number; binary: boolean; content: string | null };

export default async function SharedLinkPage({ params }: PageProps<"/s/[token]">) {
  const { token } = await params;
  const valid = /^[A-Za-z0-9_-]{20,100}$/.test(token);
  const [card, tree, readme] = valid
    ? await Promise.all([
        backendFetch<CommunityCard>(`shared/${token}`),
        backendFetch<FileEntry[]>(`shared/${token}/tree`),
        backendFetch<FileContent>(`shared/${token}/file?path=README.md`),
      ])
    : [null, null, null];
  return (
    <div className="min-h-screen">
      <header className="border-b border-border bg-surface">
        <div className="nova-gradient-bar h-1 w-full" />
        <div className="flex h-14 items-center justify-between px-4 sm:px-8">
          <Link href="/" aria-label="NOVA" className="text-text">
            <NovaLogo height={24} />
          </Link>
          <Link href="/cadastro" className="text-sm font-semibold text-primary">
            Criar conta
          </Link>
        </div>
      </header>
      <main className="px-4 py-6 sm:px-8">
        {card?.ok && tree?.ok ? (
          <CommunityProject apiBase={`/api/shared/${token}`} card={card.data} tree={tree.data} initialFile={readme?.ok ? readme.data : null} initialComments={[]} catalog={null} icons={{}} interactive={false} />
        ) : (
          <Card className="mx-auto mt-10 max-w-md text-center">
            <h1 className="text-lg font-bold">Link indisponível</h1>
            <p className="mt-2 text-sm text-muted">Este link é inválido, expirou ou foi revogado por quem compartilhou.</p>
          </Card>
        )}
      </main>
    </div>
  );
}
