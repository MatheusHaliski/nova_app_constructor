/** Preferências de interface (não são dados pessoais): tema e idioma. */
export const COOKIE_THEME = "nova_theme";
export const COOKIE_LANG = "nova_lang";

export type Theme = "SYSTEM" | "LIGHT" | "DARK";
export type Lang = "pt-BR" | "en-US" | "es-ES";

export const LANGS: Lang[] = ["pt-BR", "en-US", "es-ES"];

export function parseTheme(value: string | undefined): Theme {
  return value === "LIGHT" || value === "DARK" ? value : "SYSTEM";
}

export function parseLang(value: string | undefined): Lang {
  return value === "en-US" || value === "es-ES" ? value : "pt-BR";
}

export function themeClass(theme: Theme): string {
  return theme === "DARK" ? "dark" : theme === "LIGHT" ? "" : "theme-system";
}
