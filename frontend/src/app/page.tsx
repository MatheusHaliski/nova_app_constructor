import Link from "next/link";
import { NovaLogo } from "@/components/brand/NovaLogo";
import { getPrefs } from "@/lib/server-prefs";

export default async function Home() {
  const { t } = await getPrefs();
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-4 text-center">
      <div className="nova-gradient-bar fixed inset-x-0 top-0 h-1" />
      <NovaLogo height={120} withTagline className="max-w-full text-text" />
      <p className="max-w-xl text-lg text-muted">{t.landing.tagline}</p>
      <div className="flex flex-wrap justify-center gap-3">
        <Link href="/cadastro" className="rounded-lg bg-primary px-5 py-2.5 text-sm font-semibold text-primary-text hover:brightness-110">
          {t.landing.start}
        </Link>
        <Link href="/login" className="rounded-lg border border-border bg-surface px-5 py-2.5 text-sm font-semibold hover:bg-surface-2">
          {t.auth.login}
        </Link>
      </div>
    </main>
  );
}
