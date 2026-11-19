import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/AuthForms";
import { getPrefs } from "@/lib/server-prefs";

export const metadata: Metadata = { title: "Entrar" };

export default async function LoginPage() {
  const { t } = await getPrefs();
  return (
    <>
      <h1 className="text-2xl font-bold">{t.auth.loginTitle}</h1>
      <p className="mb-6 mt-1 text-sm text-muted">{t.auth.loginSubtitle}</p>
      <Suspense>
        <LoginForm t={t} />
      </Suspense>
    </>
  );
}
