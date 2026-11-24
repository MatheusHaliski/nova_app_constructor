/** Tipos do catálogo, da especificação e do plano de geração (espelham a API do backend). */

export type BuildTool = { id: string; name: string; icon: string; build: string; run: string; test: string };

export type Pkg = {
  id: string;
  name: string;
  kind: "dependency" | "plugin";
  artifact: string;
  required: boolean;
  default: boolean;
  needs?: string | null;
  feature?: string | null;
  scope?: string | null;
  buildTools?: string[] | null;
  description: string;
};

export type Stack = {
  id: string;
  name: string;
  icon: string;
  status: "available" | "planned";
  description: string;
  languages?: string[];
  versions: string[];
  defaultVersion?: string;
  buildTools: BuildTool[];
  defaultBuildTool?: string;
  packages: Pkg[];
};

export type Language = { id: string; name: string; icon: string; versions: string[]; defaultVersion: string; description: string };
export type Database = { id: string; name: string; icon: string; status: string; versions: string[]; defaultVersion?: string; description: string };
export type Connector = { id: string; name: string; icon: string; description: string };
export type AiProvider = { id: string; name: string; icon: string; status: string; description: string; models: string[]; defaultModel?: string };
export type Feature = { id: string; name: string; description: string; default: boolean };

export type Catalog = {
  version: string;
  projectTypes: { id: string; name: string; description: string }[];
  languages: Language[];
  backends: Stack[];
  frontends: Stack[];
  databases: Database[];
  connectors: Connector[];
  aiProviders: AiProvider[];
  features: Feature[];
};

export type ProjectSpec = {
  name: string;
  description?: string | null;
  objective?: string | null;
  type: string;
  mode: "COPILOT" | "ASSISTED";
  backend: { id: string; version?: string | null; language?: string | null; languageVersion?: string | null; buildTool?: string | null; packages?: string[] | null };
  frontend?: { id: string; version?: string | null; packages?: string[] | null } | null;
  database?: { id: string; version?: string | null } | null;
  connectors: string[];
  ai?: { provider: string; model?: string | null } | null;
  features: string[];
  screens: string[];
};

export type Artifact = { coordinate: string; scope: string; kind: string; reason: string };
export type Side = {
  id: string;
  name: string;
  version: string;
  language: string;
  languageVersion: string;
  dependencies: Artifact[];
  plugins: Artifact[];
  commands: { tool: string; build: string; run: string; test: string };
};
export type Plan = {
  spec: ProjectSpec;
  slug: string;
  backend: Side;
  frontend: Side | null;
  env: { name: string; example: string; secret: boolean; usedBy: string }[];
  files: string[];
  warnings: string[];
};

export type Proposal = {
  spec: ProjectSpec;
  requirements: string[];
  backlog: { title: string; story: string }[];
  rationale: string | null;
  source: "claude" | "regras";
  notice: string | null;
};

export type ProjectSummary = {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  type: string;
  status: "ACTIVE" | "ARCHIVED" | "TRASHED";
  currentVersion: number;
  fileCount: number;
  sizeBytes: number;
  spec: ProjectSpec;
  createdAt: string;
  updatedAt: string;
  trashedAt: string | null;
  visibility: "PRIVATE" | "LINK" | "COMMUNITY" | "PUBLIC";
  forkedFromId: string | null;
  forkedFromLabel: string | null;
};

export type FileEntry = { path: string; size: number };
export type VersionSummary = { number: number; label: string | null; origin: "GENERATOR" | "USER" | "RESTORE" | "FORK"; fileCount: number; sizeBytes: number; createdAt: string };
