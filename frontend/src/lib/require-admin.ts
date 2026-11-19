import "server-only";
import { redirect } from "next/navigation";
import { getCurrentUser, isAdmin } from "./session";

/** Controle de acesso nas páginas de administração (o backend confere de novo em cada chamada). */
export async function requireAdmin() {
  const user = await getCurrentUser();
  if (!user) redirect("/login");
  if (!isAdmin(user)) redirect("/acesso-negado");
  return user;
}
