import Link from "next/link";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { getPrefs } from "@/lib/server-prefs";

export default async function AccessDenied() {
  const { t } = await getPrefs();
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
      <NovaIcon name="lock" size={48} gradient />
      <h1 className="text-2xl font-bold">{t.denied.title}</h1>
      <p className="text-muted">{t.denied.text}</p>
      <Link href="/painel" className="font-semibold text-primary">
        ← {t.common.back}
      </Link>
    </main>
  );
}
