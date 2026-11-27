import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/session";

/** Atalho da barra lateral: o perfil público da própria pessoa. */
export default async function MyProfilePage() {
  const user = await getCurrentUser();
  redirect(user ? `/pessoas/${user.username}` : "/login");
}
