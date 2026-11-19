import type { Metadata } from "next";
import "@fontsource-variable/montserrat";
import "./globals.css";
import { NovaIconGradient } from "@/components/icons/NovaIcon";
import { themeClass } from "@/lib/prefs";
import { getPrefs } from "@/lib/server-prefs";

export const metadata: Metadata = {
  title: { default: "NOVA · Software Application Constructor", template: "%s · NOVA" },
  description: "Crie, salve e compartilhe apps e web apps com formulário estratégico e IA integrada.",
  icons: { icon: "/brand/nova-mark.svg" },
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const { theme, lang } = await getPrefs();
  return (
    <html lang={lang} className={`${themeClass(theme)} h-full antialiased`}>
      <body className="min-h-full">
        <NovaIconGradient />
        {children}
      </body>
    </html>
  );
}
