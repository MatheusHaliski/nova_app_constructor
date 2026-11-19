import type { Metadata } from "next";
import { UsersTable, type AdminUser } from "@/components/admin/UsersTable";
import { Alert, PageHeader } from "@/components/ui/ui";
import { requireAdmin } from "@/lib/require-admin";
import { getPrefs } from "@/lib/server-prefs";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Usuários" };

export default async function AdminUsersPage() {
  const me = await requireAdmin();
  const [{ t }, result] = await Promise.all([getPrefs(), api<{ content: AdminUser[] }>("admin/users?size=100")]);
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="shield" title={t.admin.users} intro={t.admin.usersIntro} />
      {result.ok ? (
        <UsersTable initial={result.data.content} currentUserId={me.id} t={t} />
      ) : (
        <Alert tone="danger">{result.problem.detail ?? t.common.error}</Alert>
      )}
    </div>
  );
}
