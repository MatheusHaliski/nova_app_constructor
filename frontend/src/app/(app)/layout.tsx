import { redirect } from "next/navigation";
import { AppShell } from "@/components/shell/AppShell";
import { NAV } from "@/lib/nav";
import { getPrefs } from "@/lib/server-prefs";
import { api, getCurrentUser, isAdmin } from "@/lib/session";

/** Área autenticada: barra superior (perfil e notificações) e barra lateral com os RFs do Trello. */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const user = await getCurrentUser();
  if (!user) redirect("/login");
  const { lang, t } = await getPrefs();
  const admin = isAdmin(user);
  const unread = await api<{ unread: number }>("me/notifications/unread-count");
  const nav = NAV.map((g) => ({ ...g, items: g.items.filter((i) => !i.adminOnly || admin) })).filter((g) => g.items.length);
  return (
    <AppShell user={{ name: user.name, username: user.username, email: user.email, isAdmin: admin }} nav={nav} lang={lang} t={t} unread={unread.ok ? unread.data.unread : 0}>
      {children}
    </AppShell>
  );
}
