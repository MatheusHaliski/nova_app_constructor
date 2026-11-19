import { NovaIcon } from "@/components/icons/NovaIcon";
import { Card, PageHeader } from "@/components/ui/ui";
import { getPrefs } from "@/lib/server-prefs";

export default async function NotificationsPage() {
  const { t } = await getPrefs();
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader icon="bell" title={t.shell.notifications} />
      <Card className="flex flex-col items-center gap-3 py-12 text-center text-muted">
        <NovaIcon name="bell" size={40} gradient />
        <p>{t.shell.noNotifications}</p>
      </Card>
    </div>
  );
}
