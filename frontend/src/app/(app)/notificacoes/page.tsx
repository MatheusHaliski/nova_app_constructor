import { NotificationsList } from "@/components/community/NotificationsList";
import { Alert, PageHeader } from "@/components/ui/ui";
import type { NotificationList } from "@/lib/community-types";
import { getPrefs } from "@/lib/server-prefs";
import { api } from "@/lib/session";

export default async function NotificationsPage() {
  const [{ t }, result] = await Promise.all([getPrefs(), api<NotificationList>("me/notifications")]);
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader icon="bell" title={t.shell.notifications} />
      {result.ok ? <NotificationsList initial={result.data.items} empty={t.shell.noNotifications} /> : <Alert tone="danger">{t.common.error}</Alert>}
    </div>
  );
}
