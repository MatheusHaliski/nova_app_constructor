import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { PinManager } from "@/components/community/PinManager";
import { ProjectCardGrid } from "@/components/community/ProjectCardGrid";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { Card, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";
import type { CommunityCard } from "@/lib/community-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Perfil" };

type Profile = {
  name: string; username: string; bio: string | null; websiteUrl: string | null; githubUsername: string | null; memberSince: string;
  pinned: CommunityCard[]; projects: CommunityCard[]; stacks: string[]; contributions: number; starsReceived: number; mine: boolean;
};

/** Perfil público com portfólio: projetos fixados, stacks usadas e contribuições aceitas em projetos de outras pessoas. */
export default async function PersonPage({ params }: PageProps<"/pessoas/[username]">) {
  const { username } = await params;
  if (!/^[a-z0-9._]{3,30}$/.test(username)) notFound();
  const [result, loaded] = await Promise.all([api<Profile>(`people/${username}`), loadCatalog()]);
  if (!result.ok || !loaded) notFound();
  const p = result.data;
  const stackName = (id: string) =>
    [...loaded.catalog.backends, ...loaded.catalog.frontends, ...loaded.catalog.databases].find((x) => x.id === id)?.name ?? id;
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="userCircle" title={p.name} intro={`@${p.username} · na rede NOVA desde ${new Date(p.memberSince).toLocaleDateString("pt-BR")}`} />
      <div className="grid gap-5 lg:grid-cols-[18rem_minmax(0,1fr)]">
        <Card className="flex flex-col gap-3 self-start">
          {p.bio && <p className="text-sm">{p.bio}</p>}
          <dl className="grid grid-cols-2 gap-2 text-center">
            <div className="rounded-lg bg-surface-2 p-2"><dt className="text-xs text-muted">Projetos</dt><dd className="text-xl font-bold">{p.projects.length}</dd></div>
            <div className="rounded-lg bg-surface-2 p-2"><dt className="text-xs text-muted">Estrelas</dt><dd className="text-xl font-bold">{p.starsReceived}</dd></div>
            <div className="col-span-2 rounded-lg bg-surface-2 p-2"><dt className="text-xs text-muted">Contribuições aceitas</dt><dd className="text-xl font-bold">{p.contributions}</dd></div>
          </dl>
          {p.stacks.length > 0 && (
            <div>
              <p className="mb-1 text-xs font-bold uppercase tracking-wide text-muted">Stacks</p>
              <p className="flex flex-wrap gap-1">{p.stacks.map((s) => <span key={s} className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">{stackName(s)}</span>)}</p>
            </div>
          )}
          {p.websiteUrl && <a href={p.websiteUrl} rel="noopener noreferrer nofollow" target="_blank" className="break-all text-sm font-semibold">{p.websiteUrl}</a>}
          {p.githubUsername && <a href={`https://github.com/${p.githubUsername}`} rel="noopener noreferrer nofollow" target="_blank" className="text-sm font-semibold">github.com/{p.githubUsername}</a>}
          {p.mine && <Link href="/configuracoes/privacidade" className="text-xs text-muted hover:text-text">Quem pode ver este perfil? Ajuste em Privacidade.</Link>}
        </Card>
        <div className="flex min-w-0 flex-col gap-5">
          {p.mine && <PinManager projects={p.projects} pinned={p.pinned.map((x) => x.id)} />}
          {p.pinned.length > 0 && (
            <section>
              <h2 className="mb-2 flex items-center gap-2 font-bold"><NovaIcon name="star" size={18} gradient /> Fixados</h2>
              <ProjectCardGrid cards={p.pinned} catalog={loaded.catalog} icons={loaded.icons} />
            </section>
          )}
          <section>
            <h2 className="mb-2 font-bold">Projetos publicados</h2>
            {p.projects.length === 0 ? <Card className="py-8 text-center text-sm text-muted">Nenhum projeto publicado na rede.</Card>
              : <ProjectCardGrid cards={p.projects} catalog={loaded.catalog} icons={loaded.icons} />}
          </section>
        </div>
      </div>
    </div>
  );
}
