"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { NovaIcon, type NovaIconName } from "@/components/icons/NovaIcon";
import { formatDate } from "@/components/projects/format";
import { Button, Card } from "@/components/ui/ui";
import type { AppNotification, NotificationKind } from "@/lib/community-types";
import { call } from "./client";

const ICON: Record<NotificationKind, NovaIconName> = { STAR: "star", FORK: "git", COMMENT: "feed", MODERATION: "shield" };

export function NotificationsList({ initial, empty }: { initial: AppNotification[]; empty: string }) {
  const router = useRouter();
  const [items, setItems] = useState(initial);
  const unread = items.filter((n) => !n.read).length;

  async function markAll() {
    const result = await call("/api/backend/me/notifications/read", "POST");
    if (result.ok) {
      setItems(items.map((n) => ({ ...n, read: true })));
      router.refresh();
    }
  }

  if (items.length === 0) {
    return (
      <Card className="flex flex-col items-center gap-3 py-12 text-center text-muted">
        <NovaIcon name="bell" size={40} gradient />
        <p>{empty}</p>
      </Card>
    );
  }
  return (
    <>
      <div className="mb-3 flex items-center justify-between gap-2">
        <p className="text-sm text-muted">{unread ? `${unread} não lida(s)` : "Tudo lido."}</p>
        {unread > 0 && (
          <Button variant="secondary" icon="check" onClick={markAll}>
            Marcar todas como lidas
          </Button>
        )}
      </div>
      <ul className="flex flex-col gap-2">
        {items.map((n) => {
          const content = (
            <>
              <NovaIcon name={ICON[n.kind]} gradient={!n.read} className="mt-0.5 shrink-0" />
              <span className="min-w-0">
                <span className={`block text-sm ${n.read ? "text-muted" : "font-semibold"}`}>{n.message}</span>
                <span className="block text-xs text-muted">{formatDate(n.createdAt)}</span>
              </span>
            </>
          );
          const cls = `flex items-start gap-3 rounded-xl border p-3 ${n.read ? "border-border bg-surface" : "border-primary/40 bg-primary/5"}`;
          return (
            <li key={n.id}>
              {n.link ? (
                <Link href={n.link} className={`${cls} hover:border-primary`}>
                  {content}
                </Link>
              ) : (
                <div className={cls}>{content}</div>
              )}
            </li>
          );
        })}
      </ul>
    </>
  );
}
