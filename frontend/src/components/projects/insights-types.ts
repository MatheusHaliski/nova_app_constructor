/* Contratos da análise do projeto (GET /projects/{id}/insights) e da aba Melhorias. */

export type StageStatus = "PASS" | "WARN" | "FAIL" | "NOT_RUN" | "NOT_APPLICABLE";
export type Category = "BUSINESS" | "TESTS" | "SECURITY" | "PRIVACY" | "CONFIG" | "SCREENS" | "REVIEW";

export type Stage = { id: string; label: string; status: StageStatus; summary: string; proposals: string[] };
export type LayerItem = { name: string; path: string; detail: string };
export type Layer = { role: string; label: string; help: string; items: LayerItem[] };
export type Endpoint = { method: string; path: string; handler: string; file: string; line: number; access: string };
export type Operation = "LIST" | "READ" | "CREATE" | "UPDATE" | "DELETE";
export type Resource = { path: string; name: string; entity: string | null; operations: Partial<Record<Operation, Endpoint>>; actions: Endpoint[] };
export type Field = { name: string; type: string; kind: string; required: boolean; id: boolean; writable: boolean; enumType: string | null; enumValues: string[] | null };
export type Entity = { name: string; table: string; file: string; stack: string; idType: string | null; writable: boolean; fields: Field[]; relations: string[]; repository: string | null };
export type Table = { name: string; file: string; columns: string[] };
export type Screen = { route: string; file: string; title: string | null; apiCalls: string[]; handlesEmpty: boolean; handlesError: boolean; generatedPlaceholder: boolean };
export type EnvVar = { name: string; secret: boolean; documented: boolean; usedIn: string[] };
export type Dependencies = { backendBuild: string | null; backendPackages: number; backendPlugins: number; frontendBuild: string | null; frontendPackages: number; connectors: string[]; env: EnvVar[] };
export type Option = { value: string; label: string };
export type FieldRow = { name: string; type: string; required?: boolean; values?: string[] };
export type EnvRow = { name: string; value: string; include: boolean };
export type Param = { name: string; label: string; type: "TEXT" | "SELECT" | "MULTI" | "BOOLEAN" | "LIST" | "FIELDS" | "ENV" | "HIDDEN"; value: unknown; options: Option[] | null; help: string | null };
export type Proposal = {
  id: string; kind: string; category: Category; severity: "GAP" | "SUGGESTION"; part: string; title: string; why: string; where: string[];
  params: Param[]; automatic: boolean; aiAvailable: boolean; howTo: string | null; state: "OPEN" | "DISMISSED";
};
export type Applied = { proposalId: string; title: string; version: number; usedAi: boolean; appliedAt: string };
export type RequirementStatus = { text: string; acceptance: string | null; priority: string; status: "COVERED" | "PARTIAL" | "MISSING" | "UNKNOWN"; evidence: string[]; missing: string[] };
export type Flow = { method: string; path: string; screens: string[]; controller: string; services: string[]; repository: string | null; entity: string | null; table: string | null; access: string };
export type PrivacyFinding = { entity: string; field: string; kind: string; publicRead: boolean; articles: string[]; advice: string };
export type A11yFinding = { file: string; line: number; rule: string; message: string };
export type Readiness = { percent: number; steps: { id: string; label: string; done: boolean; pending: number; next: string | null }[] };
export type Stack = { backend: string; backendLanguage: string | null; backendBuild: string | null; frontend: string | null; database: string | null; auth: boolean; jpa: boolean; flyway: boolean };

export type Insights = {
  version: number; analyzedAt: string; stack: Stack; stages: Stage[]; layers: Layer[]; resources: Resource[]; endpoints: Endpoint[]; entities: Entity[];
  tables: Table[]; screens: Screen[]; dependencies: Dependencies; proposals: Proposal[]; applied: Applied[]; aiAvailable: boolean; fileCount: number;
  testCount: number; requirements: RequirementStatus[]; flows: Flow[]; privacy: PrivacyFinding[]; accessibility: A11yFinding[]; readiness: Readiness;
};

export type FileChange = { path: string; action: "NEW" | "EDIT"; before: string | null; after: string; why: string | null };
export type ChangeSet = { changes: FileChange[]; notes: string[]; summary: string; usedAi: boolean };
export type Preview = { baseVersion: number; nextVersion: number; changeSet: ChangeSet };

export type PreviewPage = { route: string; file: string; title: string | null; apiCalls: string[]; handlesEmpty: boolean; handlesError: boolean };
export type Mock = { method: string; path: string; body: unknown; access: string };
export type PreviewBundle = { version: number; framework: string | null; root: string; pages: PreviewPage[]; sources: Record<string, string>; mocks: Mock[]; truncated: boolean };

export type FileDiff = { path: string; change: "ADDED" | "REMOVED" | "CHANGED"; added: number; removed: number; diff: string };
export type Comparison = {
  from: number; to: number; files: FileDiff[]; endpointsAdded: string[]; endpointsRemoved: string[]; entitiesAdded: string[]; entitiesRemoved: string[];
  screensAdded: string[]; screensRemoved: string[];
};

export type OpenFile = (path: string, line?: number) => void;
