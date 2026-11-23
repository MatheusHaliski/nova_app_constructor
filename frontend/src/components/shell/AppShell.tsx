"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { NovaLogo, NovaMark } from "@/components/brand/NovaLogo";
import { NovaIcon } from "@/components/icons/NovaIcon";
import type { Dict } from "@/lib/i18n";
import type { NavGroup } from "@/lib/nav";
import type { Lang } from "@/lib/prefs";

type ShellUser = { name: string; username: string; email: string; isAdmin: boolean };

const COLLAPSE_KEY = "nova.sidebar.collapsed";
const COLLAPSE_EVENT = "nova-sidebar";

/** Preferência local de barra recolhida (conveniência do navegador; não é dado pessoal). */
function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === "1";
  } catch {
    return false;
  }
}

function subscribeCollapsed(callback: () => void) {
  window.addEventListener(COLLAPSE_EVENT, callback);
  window.addEventListener("storage", callback);
  return () => {
    window.removeEventListener(COLLAPSE_EVENT, callback);
    window.removeEventListener("storage", callback);
  };
}

export function AppShell({
  user,
  nav,
  lang,
  t,
  children,
}: {
  user: ShellUser;
  nav: NavGroup[];
  lang: Lang;
  t: Dict;
  children: ReactNode;
}) {
  const collapsed = useSyncExternalStore(subscribeCollapsed, readCollapsed, () => false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const pathname = usePathname();

  function toggleCollapsed() {
    try {
      localStorage.setItem(COLLAPSE_KEY, collapsed ? "0" : "1");
    } catch {
      // armazenamento indisponível
    }
    window.dispatchEvent(new Event(COLLAPSE_EVENT));
  }

  return (
    <div className="flex min-h-screen flex-col">
      <TopBar user={user} t={t} onOpenMenu={() => setMobileOpen(true)} />
      <div className="flex flex-1">
        {mobileOpen && (
          <button
            aria-label={t.common.cancel}
            className="fixed inset-0 z-30 bg-black/40 lg:hidden"
            onClick={() => setMobileOpen(false)}
          />
        )}
        <SideNav
          nav={nav}
          lang={lang}
          t={t}
          pathname={pathname}
          collapsed={collapsed}
          mobileOpen={mobileOpen}
          onToggle={toggleCollapsed}
          onNavigate={() => setMobileOpen(false)}
        />
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-8">{children}</main>
      </div>
    </div>
  );
}

function TopBar({ user, t, onOpenMenu }: { user: ShellUser; t: Dict; onOpenMenu: () => void }) {
  const [open, setOpen] = useState<"none" | "notifications" | "profile">("none");
  const ref = useRef<HTMLDivElement>(null);
  const router = useRouter();

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen("none");
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen("none");
    }
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, []);

  async function logout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.replace("/login");
    router.refresh();
  }

  const initials = user.name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join("");

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-surface/90 backdrop-blur">
      <div className="nova-gradient-bar h-1 w-full" />
      <div className="flex h-14 items-center justify-between gap-3 px-3 sm:px-5">
        <div className="flex items-center gap-2">
          <button
            className="rounded-lg p-2 text-text hover:bg-surface-2 lg:hidden"
            onClick={onOpenMenu}
            aria-label={t.shell.openMenu}
          >
            <NovaIcon name="menu" />
          </button>
          <Link href="/painel" aria-label="NOVA" className="flex items-center gap-2 text-text">
            <NovaLogo height={24} />
          </Link>
        </div>
        <div ref={ref} className="relative flex items-center gap-1">
          <button
            className="relative rounded-lg p-2 text-text hover:bg-surface-2"
            aria-label={t.shell.notifications}
            aria-expanded={open === "notifications"}
            onClick={() => setOpen(open === "notifications" ? "none" : "notifications")}
          >
            <NovaIcon name="bell" />
          </button>
          <button
            className="flex items-center gap-2 rounded-full border border-border py-1 pl-1 pr-2 hover:bg-surface-2"
            aria-label={t.shell.profile}
            aria-expanded={open === "profile"}
            onClick={() => setOpen(open === "profile" ? "none" : "profile")}
          >
            <span className="nova-gradient-bar flex h-8 w-8 items-center justify-center rounded-full text-xs font-bold text-white">
              {initials || <NovaIcon name="user" size={16} />}
            </span>
            <NovaIcon name="chevronDown" size={16} className="text-muted" />
          </button>

          {open === "notifications" && (
            <div className="absolute right-12 top-12 w-72 rounded-xl border border-border bg-surface p-4 shadow-xl">
              <p className="mb-2 text-sm font-semibold">{t.shell.notifications}</p>
              <div className="flex flex-col items-center gap-2 py-4 text-center text-sm text-muted">
                <NovaIcon name="bell" size={28} gradient />
                {t.shell.noNotifications}
              </div>
              <Link href="/notificacoes" className="block text-center text-xs font-semibold text-primary">
                {t.shell.notifications} →
              </Link>
            </div>
          )}

          {open === "profile" && (
            <div className="absolute right-0 top-12 w-64 rounded-xl border border-border bg-surface p-2 shadow-xl" role="menu">
              <div className="border-b border-border px-3 py-2">
                <p className="truncate text-sm font-semibold">{user.name}</p>
                <p className="truncate text-xs text-muted">@{user.username}</p>
                {user.isAdmin && (
                  <span className="mt-1 inline-block rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
                    {t.shell.admin}
                  </span>
                )}
              </div>
              <MenuLink href="/configuracoes/conta" icon="user" label={t.shell.profile} />
              <MenuLink href="/configuracoes/privacidade" icon="eyeOff" label={t.settings.privacy} />
              <MenuLink href="/configuracoes/aparencia" icon="sliders" label={t.shell.settings} />
              <button
                role="menuitem"
                onClick={logout}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-danger hover:bg-surface-2"
              >
                <NovaIcon name="logout" size={16} />
                {t.auth.logout}
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

function MenuLink({ href, icon, label }: { href: string; icon: "user" | "eyeOff" | "sliders"; label: string }) {
  return (
    <Link role="menuitem" href={href} className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm hover:bg-surface-2">
      <NovaIcon name={icon} size={16} />
      {label}
    </Link>
  );
}

function SideNav({
  nav,
  lang,
  t,
  pathname,
  collapsed,
  mobileOpen,
  onToggle,
  onNavigate,
}: {
  nav: NavGroup[];
  lang: Lang;
  t: Dict;
  pathname: string;
  collapsed: boolean;
  mobileOpen: boolean;
  onToggle: () => void;
  onNavigate: () => void;
}) {
  const width = collapsed ? "lg:w-[72px]" : "lg:w-64";
  // Um único item ativo: o primeiro cujo caminho bate com a página atual (vários itens podem apontar para a mesma tela).
  const activeSlug = nav
    .flatMap((g) => g.items)
    .find((item) => {
      const path = item.href.split("?")[0].replace(/\/conta$/, "");
      return pathname === path || (path !== "/painel" && pathname.startsWith(`${path}/`)) || pathname === `/em-breve/${item.slug}`;
    })?.slug;
  return (
    <aside
      className={`fixed inset-y-0 left-0 z-40 w-72 transform border-r border-border bg-surface transition-transform lg:sticky lg:top-[60px] lg:h-[calc(100vh-60px)] lg:translate-x-0 ${width} ${
        mobileOpen ? "translate-x-0" : "-translate-x-full"
      }`}
      aria-label="Menu principal"
    >
      <div className="flex h-full flex-col">
        <div className="flex items-center justify-between gap-2 border-b border-border px-3 py-3">
          <div className="flex items-center gap-2">
            <NovaMark size={30} />
            {!collapsed && <span className="text-sm font-bold tracking-wide nova-gradient-text">NOVA</span>}
          </div>
          <button
            onClick={onToggle}
            className="hidden rounded-lg p-1.5 text-muted hover:bg-surface-2 lg:block"
            aria-label={collapsed ? t.shell.expand : t.shell.collapse}
          >
            <NovaIcon name={collapsed ? "chevronRight" : "chevronLeft"} size={18} />
          </button>
        </div>
        <nav className="flex-1 overflow-y-auto px-2 py-3">
          {nav.map((group) => (
            <div key={group.id} className="mb-4">
              {!collapsed && (
                <p className="px-3 pb-1 text-[11px] font-bold uppercase tracking-wider text-muted">{group.label[lang]}</p>
              )}
              <ul className="flex flex-col gap-0.5">
                {group.items.map((item) => {
                  const active = item.slug === activeSlug;
                  return (
                    <li key={item.slug}>
                      <Link
                        href={item.href}
                        onClick={onNavigate}
                        title={collapsed ? `${item.label[lang]} · ${item.rf}` : item.rf}
                        aria-current={active ? "page" : undefined}
                        className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${
                          active ? "bg-primary/10 font-semibold text-text" : "text-muted hover:bg-surface-2 hover:text-text"
                        } ${collapsed ? "lg:justify-center lg:px-0" : ""}`}
                      >
                        <NovaIcon name={item.icon} size={20} gradient={active} />
                        <span className={`flex-1 truncate ${collapsed ? "lg:hidden" : ""}`}>{item.label[lang]}</span>
                        {!item.ready && (
                          <span className={`rounded-full bg-surface-2 px-1.5 py-0.5 text-[10px] font-semibold text-muted ${collapsed ? "lg:hidden" : ""}`}>
                            F{item.phase}
                          </span>
                        )}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </div>
    </aside>
  );
}
