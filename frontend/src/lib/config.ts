import "server-only";

/** URL do backend Spring Boot, usada só no servidor (nunca exposta ao navegador). */
export const API_URL = process.env.NOVA_API_URL ?? "http://localhost:8080";

export const COOKIE_ACCESS = "nova_at";
export const COOKIE_REFRESH = "nova_rt";

export const secureCookies = process.env.NODE_ENV === "production";
