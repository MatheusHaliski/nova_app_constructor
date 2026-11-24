import type { ProjectSpec } from "./catalog-types";

export type Visibility = "PRIVATE" | "LINK" | "COMMUNITY" | "PUBLIC";
export type License = "MIT" | "APACHE_2_0" | "GPL_3_0" | "ALL_RIGHTS_RESERVED";

export const VISIBILITY: Record<Visibility, { label: string; icon: "lock" | "share" | "feed" | "globe"; description: string }> = {
  PRIVATE: { label: "Privado", icon: "lock", description: "Só você vê. É como todo projeto nasce." },
  LINK: { label: "Link", icon: "share", description: "Quem tiver um link que você criar vê o projeto, sem precisar de conta. Não aparece na rede." },
  COMMUNITY: { label: "Comunidade", icon: "feed", description: "Aparece no feed e na busca para quem está logado na rede NOVA." },
  PUBLIC: { label: "Público", icon: "globe", description: "Aparece na rede com destaque de público e pode ser compartilhado por link livremente." },
};

export const LICENSES: Record<License, { label: string; description: string }> = {
  MIT: { label: "MIT", description: "Permissiva: uso, cópia e modificação livres, mantendo o aviso de autoria." },
  APACHE_2_0: { label: "Apache 2.0", description: "Permissiva, com concessão explícita de patentes." },
  GPL_3_0: { label: "GPL 3.0", description: "Quem distribuir versões modificadas precisa abrir o código na mesma licença." },
  ALL_RIGHTS_RESERVED: { label: "Todos os direitos reservados", description: "As pessoas podem ver, mas não podem fazer fork nem reutilizar." },
};

export type CommunityCard = {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  type: string;
  spec: ProjectSpec;
  visibility: Visibility;
  license: License | null;
  author: string;
  stars: number;
  forks: number;
  comments: number;
  starredByMe: boolean;
  mine: boolean;
  fileCount: number;
  sizeBytes: number;
  version: number;
  publishedAt: string | null;
  updatedAt: string;
  forkedFromId: string | null;
  forkedFromLabel: string | null;
};

export type CommunityComment = { id: number; author: string | null; body: string; createdAt: string; mine: boolean; canDelete: boolean };

export type ShareLink = { id: string; createdAt: string; expiresAt: string | null; lastUsedAt: string | null };
export type SharingState = { visibility: Visibility; license: License | null; hiddenPaths: string[]; links: ShareLink[]; publishedAt: string | null };

export type NotificationKind = "STAR" | "FORK" | "COMMENT" | "MODERATION";
export type AppNotification = { id: number; kind: NotificationKind; message: string; link: string | null; read: boolean; createdAt: string };
export type NotificationList = { unread: number; items: AppNotification[] };

export const REPORT_REASONS = {
  SPAM: "Spam ou propaganda",
  MALICIOUS_CODE: "Código malicioso",
  EXPOSED_SECRET: "Senha ou chave exposta",
  COPYRIGHT: "Violação de direitos autorais",
  OFFENSIVE: "Conteúdo ofensivo",
  OTHER: "Outro motivo",
} as const;
export type ReportReason = keyof typeof REPORT_REASONS;

export type Report = {
  id: number;
  projectId: string;
  projectName: string;
  projectAuthor: string;
  commentId: number | null;
  commentBody: string | null;
  reason: ReportReason;
  details: string | null;
  reporter: string | null;
  status: "OPEN" | "RESOLVED";
  resolution: string | null;
  createdAt: string;
  resolvedAt: string | null;
};
