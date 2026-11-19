import "server-only";
import { cookies } from "next/headers";
import { getDict } from "./i18n";
import { COOKIE_LANG, COOKIE_THEME, parseLang, parseTheme } from "./prefs";

/** Tema e idioma da requisição atual (cookies) e o dicionário correspondente. */
export async function getPrefs() {
  const store = await cookies();
  const theme = parseTheme(store.get(COOKIE_THEME)?.value);
  const lang = parseLang(store.get(COOKIE_LANG)?.value);
  return { theme, lang, t: getDict(lang) };
}
