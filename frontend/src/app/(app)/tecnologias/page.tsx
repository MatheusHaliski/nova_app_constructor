import type { Metadata } from "next";
import { TechCatalog } from "@/components/tech/TechCatalog";
import { PageHeader } from "@/components/ui/ui";
import { getPrefs } from "@/lib/server-prefs";
import { CATEGORY_ORDER, TECHS } from "@/lib/techs";

export const metadata: Metadata = { title: "Tecnologias" };

export default async function TechnologiesPage() {
  const { t } = await getPrefs();
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="chip" title={t.techs.title} intro={t.techs.intro} />
      <TechCatalog techs={TECHS} categories={CATEGORY_ORDER} t={t} />
    </div>
  );
}
