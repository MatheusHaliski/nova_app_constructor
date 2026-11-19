import Link from "next/link";
import { NovaLogo } from "@/components/brand/NovaLogo";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <div className="nova-gradient-bar fixed inset-x-0 top-0 h-1" />
      <Link href="/" className="mb-8 text-text" aria-label="NOVA">
        <NovaLogo height={56} withTagline />
      </Link>
      <div className="w-full max-w-lg rounded-2xl border border-border bg-surface p-6 shadow-lg sm:p-8">{children}</div>
    </div>
  );
}
