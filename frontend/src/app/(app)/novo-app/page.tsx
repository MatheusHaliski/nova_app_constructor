import type { Metadata } from "next";
import { CreateWizard } from "@/components/create/CreateWizard";
import { Alert, PageHeader } from "@/components/ui/ui";
import { loadCatalog } from "@/lib/catalog";
import type { ProjectSpec } from "@/lib/catalog-types";
import { api } from "@/lib/session";

export const metadata: Metadata = { title: "Novo app" };

export default async function NewAppPage({ searchParams }: PageProps<"/novo-app">) {
  const { modo, remix } = await searchParams;
  const [loaded, remixed] = await Promise.all([
    loadCatalog(),
    typeof remix === "string" && /^[0-9a-f-]{36}$/.test(remix) ? api<{ spec: ProjectSpec; origin: string }>(`community/projects/${remix}/remix`) : null,
  ]);
  const mode = modo === "copilot" ? "COPILOT" : modo === "assistido" ? "ASSISTED" : null;
  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader icon="sparkle" title="Novo app" intro="Escolha as tecnologias, os pacotes e os conectores; o NOVA gera a estrutura inicial e salva no seu perfil." />
      {loaded ? (
        <>
          {remixed && !remixed.ok && <div className="mb-4"><Alert tone="warning">{remixed.problem.detail ?? "Não foi possível remixar este projeto."}</Alert></div>}
          {remixed?.ok && <div className="mb-4"><Alert tone="info">Remix de {remixed.data.origin}: o formulário veio preenchido com as escolhas do projeto original. Ajuste o que quiser.</Alert></div>}
          <CreateWizard key={mode ?? (remixed?.ok ? "remix" : "escolher")} catalog={loaded.catalog} icons={loaded.icons} initialMode={mode}
            remix={remixed?.ok ? remixed.data : null} />
        </>
      ) : (
        <Alert tone="danger">Não foi possível carregar o catálogo de tecnologias.</Alert>
      )}
    </div>
  );
}
