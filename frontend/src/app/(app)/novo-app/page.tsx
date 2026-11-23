import type { Metadata } from "next";
import { CreateWizard } from "@/components/create/CreateWizard";
import { Alert, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";

export const metadata: Metadata = { title: "Novo app" };

export default async function NewAppPage({ searchParams }: PageProps<"/novo-app">) {
  const { modo } = await searchParams;
  const loaded = await loadCatalog();
  const mode = modo === "copilot" ? "COPILOT" : modo === "assistido" ? "ASSISTED" : null;
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="sparkle" title="Novo app" intro="Escolha as tecnologias, os pacotes e os conectores; o NOVA gera a estrutura inicial e salva no seu perfil." />
      {loaded ? (
        <CreateWizard key={mode ?? "escolher"} catalog={loaded.catalog} icons={loaded.icons} initialMode={mode} />
      ) : (
        <Alert tone="danger">Não foi possível carregar o catálogo de tecnologias.</Alert>
      )}
    </div>
  );
}
