export type ConsentPurpose = "PERSONALIZED_FEED" | "PRODUCT_ANALYTICS" | "MARKETING_EMAILS" | "AI_IMPROVEMENT" | "PARTNER_SHARING";
export type Visibility = "PRIVATE" | "COMMUNITY" | "PUBLIC";

export type Settings = {
  account: {
    name: string; username: string; email: string; emailVerified: boolean; birthDate: string; bio: string | null;
    websiteUrl: string | null; githubUsername: string | null; lastLoginAt: string | null; passwordChangedAt: string | null;
  };
  privacy: { profileVisibility: Visibility; consents: Record<ConsentPurpose, { granted: boolean; updatedAt: string | null }> };
  preferences: { theme: "SYSTEM" | "LIGHT" | "DARK"; language: "pt-BR" | "en-US" | "es-ES" };
  terms: { version: string; acceptedAt: string };
};

export const SECTIONS = ["conta", "privacidade", "aparencia", "idioma", "seus-dados"] as const;
export type Section = (typeof SECTIONS)[number];
