import type { NovaIconName } from "@/components/icons/NovaIcon";
import type { Lang } from "./prefs";

/**
 * Barra lateral: um item para cada grupo de requisitos funcionais (RF) do Trello, organizado pelos objetivos.
 * Itens com `ready` funcionam na fase atual; os demais abrem a página "Em construção" com o RF e a fase prevista.
 */

type Label = Record<Lang, string>;

export type NavItem = {
  slug: string;
  href: string;
  icon: NovaIconName;
  label: Label;
  rf: string;
  phase: number;
  ready?: boolean;
  adminOnly?: boolean;
  description: Label;
};

export type NavGroup = { id: string; label: Label; items: NavItem[] };

const soon = (slug: string) => `/em-breve/${slug}`;
const L = (pt: string, en: string, es: string): Label => ({ "pt-BR": pt, "en-US": en, "es-ES": es });

export const TRELLO_BOARD_URL = "https://trello.com/b/hqxCZt1b/nova-application-constructor";

export const NAV: NavGroup[] = [
  {
    id: "inicio",
    label: L("Início", "Home", "Inicio"),
    items: [
      { slug: "painel", href: "/painel", icon: "home", label: L("Painel", "Dashboard", "Panel"), rf: "—", phase: 1, ready: true, description: L("Visão geral", "Overview", "Resumen") },
    ],
  },
  {
    id: "obj1",
    label: L("Criar app", "Create app", "Crear app"),
    items: [
      { slug: "novo-app", href: "/novo-app", ready: true, icon: "sparkle", label: L("Novo app", "New app", "Nueva app"), rf: "Obj 1 · Formulário de criação", phase: 3, description: L("Escolha o modo (Copilot total ou assistido) e a stack: backend, linguagem, frontend, banco e IA externa.", "Pick the mode (full Copilot or assisted) and the stack.", "Elige el modo (Copilot total o asistido) y la stack.") },
      { slug: "copilot", href: "/novo-app?modo=copilot", ready: true, icon: "copilot", label: L("Copilot total", "Full Copilot", "Copilot total"), rf: "Obj 1 · Modo Copilot total", phase: 3, description: L("A IA preenche objetivo, requisitos, backlog e diagramas a partir da sua ideia.", "AI fills goal, requirements, backlog and diagrams from your idea.", "La IA completa objetivo, requisitos, backlog y diagramas.") },
      { slug: "assistido", href: "/novo-app?modo=assistido", ready: true, icon: "assist", label: L("Formulário assistido", "Assisted form", "Formulario asistido"), rf: "Obj 1 · Modo assistido", phase: 3, description: L("Você preenche cada campo com a IA sugerindo e melhorando.", "You fill each field with AI suggestions.", "Completas cada campo con sugerencias de la IA.") },
      { slug: "telas", href: soon("telas"), icon: "screens", label: L("Telas", "Screens", "Pantallas"), rf: "Obj 1 · ScreenSpec", phase: 3, description: L("Número de telas, elementos, estilo, tamanho, alinhamento e autenticação opcional.", "Screens, elements, style, size, alignment and optional auth.", "Pantallas, elementos, estilo, tamaño, alineación y autenticación opcional.") },
      { slug: "backlog-diagramas", href: soon("backlog-diagramas"), icon: "diagram", label: L("Backlog e diagramas", "Backlog and diagrams", "Backlog y diagramas"), rf: "Obj 1 · Geração de backlog e diagramas", phase: 3, description: L("Épicos, histórias e diagramas Mermaid gerados com IA.", "Epics, stories and Mermaid diagrams generated with AI.", "Épicas, historias y diagramas Mermaid con IA.") },
    ],
  },
  {
    id: "obj2",
    label: L("Meus projetos", "My projects", "Mis proyectos"),
    items: [
      { slug: "projetos", href: "/projetos", ready: true, icon: "folder", label: L("Projetos", "Projects", "Proyectos"), rf: "RF-2.10 · Organização e ciclo de vida", phase: 2, description: L("Lista, filtros, coleções, duplicar, arquivar e lixeira.", "List, filters, collections, duplicate, archive and trash.", "Lista, filtros, colecciones, duplicar, archivar y papelera.") },
      { slug: "arquivos", href: "/projetos", ready: true, icon: "tree", label: L("Arquivos", "Files", "Archivos"), rf: "RF-2.03 · Arquivos e pastas", phase: 2, description: L("Pastas, subpastas, visualização, edição, organização e upload.", "Folders, viewing, editing, organizing and upload.", "Carpetas, visualización, edición, organización y subida.") },
      { slug: "versoes", href: "/projetos", ready: true, icon: "history", label: L("Versões", "Versions", "Versiones"), rf: "RF-2.01 · Salvamento e versões", phase: 2, description: L("Salvamento automático, histórico, comparação e restauração.", "Autosave, history, compare and restore.", "Guardado automático, historial, comparación y restauración.") },
      { slug: "downloads", href: "/projetos", ready: true, icon: "download", label: L("Downloads", "Downloads", "Descargas"), rf: "RF-2.06 · Download", phase: 2, description: L("Projeto, pasta ou arquivo em ZIP, sem segredos.", "Project, folder or file as ZIP, without secrets.", "Proyecto, carpeta o archivo en ZIP, sin secretos.") },
      { slug: "integracoes", href: soon("integracoes"), icon: "git", label: L("Integrações", "Integrations", "Integraciones"), rf: "RF-2.07 · Git, IDE, CLI e API", phase: 6, description: L("GitHub/GitLab, VS Code, CLI, API e webhooks.", "GitHub/GitLab, VS Code, CLI, API and webhooks.", "GitHub/GitLab, VS Code, CLI, API y webhooks.") },
      { slug: "qualidade", href: soon("qualidade"), icon: "shieldCheck", label: L("Qualidade e segurança", "Quality and security", "Calidad y seguridad"), rf: "RF-2.08 · Qualidade e segurança", phase: 2, description: L("Varredura de segredos, dependências, build e testes.", "Secret scanning, dependencies, build and tests.", "Escaneo de secretos, dependencias, build y pruebas.") },
      { slug: "deploy", href: soon("deploy"), icon: "rocket", label: L("Execução e deploy", "Run and deploy", "Ejecución y deploy"), rf: "RF-2.09 · Execução e deploy", phase: 6, description: L("Prévia em sandbox e publicação.", "Sandbox preview and publishing.", "Vista previa en sandbox y publicación.") },
    ],
  },
  {
    id: "obj3",
    label: L("Rede NOVA", "NOVA network", "Red NOVA"),
    items: [
      { slug: "feed", href: "/feed", ready: true, icon: "feed", label: L("Feed", "Feed", "Feed"), rf: "RF-3.03 · Rede NOVA", phase: 4, description: L("Cards compactos dos projetos publicados.", "Compact cards of published projects.", "Tarjetas compactas de proyectos publicados.") },
      { slug: "explorar", href: "/explorar", ready: true, icon: "compass", label: L("Explorar", "Explore", "Explorar"), rf: "RF-3.03 · Busca e descoberta", phase: 4, description: L("Busca por stack, arquitetura e tópico, com ícones das tecnologias.", "Search by stack, architecture and topic.", "Búsqueda por stack, arquitectura y tema.") },
      { slug: "compartilhados", href: "/compartilhados", ready: true, icon: "share", label: L("Compartilhados", "Shared", "Compartidos"), rf: "RF-3.02 · Compartilhamento", phase: 4, description: L("Seus projetos visíveis para outras pessoas, links e visibilidade.", "Your projects visible to others, links and visibility.", "Tus proyectos visibles para otros, enlaces y visibilidad.") },
      { slug: "estrelas", href: "/estrelas", ready: true, icon: "star", label: L("Estrelas", "Stars", "Estrellas"), rf: "RF-3.03 · Estrelas e forks", phase: 4, description: L("Projetos estrelados e forks com atribuição.", "Starred projects and attributed forks.", "Proyectos con estrella y forks.") },
      { slug: "perfil-publico", href: soon("perfil-publico"), icon: "userCircle", label: L("Perfil público", "Public profile", "Perfil público"), rf: "RF-3.03 · Perfil e portfólio", phase: 4, description: L("Portfólio com projetos fixados.", "Portfolio with pinned projects.", "Portafolio con proyectos fijados.") },
      { slug: "moderacao", href: "/admin/moderacao", ready: true, adminOnly: true, icon: "shield", label: L("Moderação", "Moderation", "Moderación"), rf: "RF-3.04 · Segurança da comunidade", phase: 4, description: L("Denúncias, bloqueios e recursos.", "Reports, blocks and appeals.", "Denuncias, bloqueos y recursos.") },
    ],
  },
  {
    id: "obj4",
    label: L("Abstração visual", "Visual abstraction", "Abstracción visual"),
    items: [
      { slug: "tecnologias", href: "/tecnologias", icon: "chip", label: L("Tecnologias", "Technologies", "Tecnologías"), rf: "RF-4.04 / RF-4.25 · Acervo e fichas", phase: 1, ready: true, description: L("Catálogo de tecnologias com ícones.", "Technology catalog with icons.", "Catálogo de tecnologías con íconos.") },
      { slug: "arquitetura", href: soon("arquitetura"), icon: "layers", label: L("Arquitetura", "Architecture", "Arquitectura"), rf: "RF-4.06 · Catálogo de arquiteturas", phase: 5, description: L("Camadas, hexagonal, clean, modular, multimódulo e mais.", "Layered, hexagonal, clean, modular, multi-module and more.", "Capas, hexagonal, clean, modular, multimódulo y más.") },
      { slug: "dados", href: soon("dados"), icon: "database", label: L("Dados", "Data", "Datos"), rf: "RF-4.11 / RF-4.12 · Entidades e migrações", phase: 5, description: L("Entidades, relacionamentos e migrações seguras.", "Entities, relationships and safe migrations.", "Entidades, relaciones y migraciones seguras.") },
      { slug: "api", href: soon("api"), icon: "api", label: L("API", "API", "API"), rf: "RF-4.13 / RF-4.14 · Designer de API", phase: 5, description: L("CRUD, endpoints personalizados e OpenAPI.", "CRUD, custom endpoints and OpenAPI.", "CRUD, endpoints personalizados y OpenAPI.") },
      { slug: "servicos", href: soon("servicos"), icon: "services", label: L("Serviços", "Services", "Servicios"), rf: "RF-4.15 / RF-4.16 · Casos de uso e regras", phase: 5, description: L("Casos de uso em blocos, regras, eventos e agendamentos.", "Use cases, rules, events and schedules.", "Casos de uso, reglas, eventos y tareas.") },
      { slug: "integracoes-externas", href: soon("integracoes-externas"), icon: "plug", label: L("Integrações externas", "External integrations", "Integraciones externas"), rf: "RF-4.17 · Integrações e IA externa", phase: 5, description: L("APIs externas e IA configurável.", "External APIs and configurable AI.", "APIs externas e IA configurable.") },
      { slug: "seguranca-app", href: soon("seguranca-app"), icon: "lock", label: L("Segurança do app", "App security", "Seguridad de la app"), rf: "RF-4.18 · Perfis e permissões", phase: 5, description: L("Matriz de perfis e políticas de autenticação.", "Role matrix and auth policies.", "Matriz de perfiles y políticas de autenticación.") },
      { slug: "configuracoes-app", href: soon("configuracoes-app"), icon: "sliders", label: L("Configurações do app", "App configuration", "Configuración de la app"), rf: "RF-4.07 a RF-4.10 · Configurações e recursos", phase: 5, description: L("Configurações por formulário, ambientes, segredos e recursos.", "Form-based config, environments, secrets and features.", "Configuración por formulario, entornos, secretos y recursos.") },
      { slug: "frontend", href: soon("frontend"), icon: "layout", label: L("Frontend", "Frontend", "Frontend"), rf: "RF-4.34 · Camadas do frontend", phase: 5, description: L("Rotas, renderização, estado e componentes.", "Routes, rendering, state and components.", "Rutas, renderizado, estado y componentes.") },
      { slug: "didatico", href: soon("didatico"), icon: "graduation", label: L("Modo didático", "Learning mode", "Modo didáctico"), rf: "RF-4.23 · Modo didático", phase: 5, description: L("Explicações de cada conceito e arquivo.", "Explanations of each concept and file.", "Explicaciones de cada concepto y archivo.") },
    ],
  },
  {
    id: "conta",
    label: L("Conta", "Account", "Cuenta"),
    items: [
      { slug: "configuracoes", href: "/configuracoes/conta", icon: "sliders", label: L("Configurações", "Settings", "Configuración"), rf: "RF-3.05 · LGPD (perfil)", phase: 1, ready: true, description: L("Conta, privacidade, aparência, idioma e seus dados.", "Account, privacy, appearance, language and your data.", "Cuenta, privacidad, apariencia, idioma y tus datos.") },
      { slug: "admin-usuarios", href: "/admin/usuarios", icon: "shield", label: L("Usuários", "Users", "Usuarios"), rf: "Controle de acesso", phase: 1, ready: true, adminOnly: true, description: L("Perfis de acesso.", "Access roles.", "Perfiles de acceso.") },
      { slug: "admin-sistema", href: "/admin/sistema", icon: "server", label: L("Sistema", "System", "Sistema"), rf: "Fontes de dados", phase: 1, ready: true, adminOnly: true, description: L("MySQL e conectores opcionais.", "MySQL and optional connectors.", "MySQL y conectores opcionales.") },
    ],
  },
];

export const PHASES: { phase: number; title: Label }[] = [
  { phase: 1, title: L("Fundação: conta, acesso, privacidade (LGPD) e tecnologias", "Foundation: account, access, privacy and technologies", "Base: cuenta, acceso, privacidad y tecnologías") },
  { phase: 2, title: L("Meus projetos: arquivos, versões e download", "My projects: files, versions and download", "Mis proyectos: archivos, versiones y descarga") },
  { phase: 3, title: L("Criar app: formulário, Copilot, telas e IA", "Create app: form, Copilot, screens and AI", "Crear app: formulario, Copilot, pantallas e IA") },
  { phase: 4, title: L("Rede NOVA: compartilhamento, feed e moderação", "NOVA network: sharing, feed and moderation", "Red NOVA: compartir, feed y moderación") },
  { phase: 5, title: L("Abstração visual do backend e do frontend", "Visual abstraction of backend and frontend", "Abstracción visual de backend y frontend") },
  { phase: 6, title: L("Integrações, execução e conectores opcionais", "Integrations, execution and optional connectors", "Integraciones, ejecución y conectores opcionales") },
];

export function findNavItem(slug: string): NavItem | undefined {
  return NAV.flatMap((g) => g.items).find((i) => i.slug === slug);
}
