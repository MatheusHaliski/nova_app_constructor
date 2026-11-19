import type { Metadata } from "next";
import { NovaIcon } from "@/components/icons/NovaIcon";
import { TechIcon } from "@/components/tech/TechIcon";
import { Alert, Card, PageHeader } from "@/components/ui/ui";
import { requireAdmin } from "@/lib/require-admin";
import { getPrefs } from "@/lib/server-prefs";
import { api } from "@/lib/session";
import { TECHS } from "@/lib/techs";

export const metadata: Metadata = { title: "Sistema" };

type DataSource = { id: string; name: string; role: string; required: boolean; enabled: boolean; purpose: string };

const TECH_ID: Record<string, string> = { mysql: "mysql", redis: "redis", opensearch: "opensearch", s3: "s3", cassandra: "apachecassandra" };

export default async function AdminSystemPage() {
  await requireAdmin();
  const [{ t }, result] = await Promise.all([getPrefs(), api<DataSource[]>("admin/system/data-sources")]);
  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader icon="server" title={t.admin.system} intro={t.admin.systemIntro} />
      {!result.ok ? (
        <Alert tone="danger">{result.problem.detail ?? t.common.error}</Alert>
      ) : (
        <ul className="grid gap-3 md:grid-cols-2">
          {result.data.map((ds) => {
            const tech = TECHS.find((x) => x.id === TECH_ID[ds.id]);
            return (
              <li key={ds.id}>
                <Card className={ds.required ? "border-primary/40" : ""}>
                  <div className="flex items-start gap-3">
                    {tech ? <TechIcon tech={tech} size={44} /> : <NovaIcon name="database" size={32} />}
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="font-bold">{ds.name}</p>
                        <span
                          className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                            ds.required ? "bg-primary/15 text-primary" : "bg-surface-2 text-muted"
                          }`}
                        >
                          {ds.required ? t.admin.central : t.admin.optional}
                        </span>
                      </div>
                      <p className="mt-1 text-sm text-muted">{ds.purpose}</p>
                    </div>
                    <span className={`text-xs font-bold ${ds.enabled ? "text-success" : "text-muted"}`}>
                      ● {ds.enabled ? t.admin.on : t.admin.off}
                    </span>
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
