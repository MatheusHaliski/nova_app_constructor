import type { Metadata } from "next";
import { RegisterForm } from "@/components/auth/AuthForms";
import { getPrefs } from "@/lib/server-prefs";

export const metadata: Metadata = { title: "Criar conta" };

export default async function RegisterPage() {
  const { t } = await getPrefs();
  return (
    <>
      <h1 className="text-2xl font-bold">{t.auth.registerTitle}</h1>
      <p className="mb-6 mt-1 text-sm text-muted">{t.auth.registerSubtitle}</p>
      <RegisterForm t={t} />
    </>
  );
}
