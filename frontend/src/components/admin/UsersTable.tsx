"use client";

import { useState } from "react";
import { Alert, Button } from "@/components/ui/ui";
import type { Dict } from "@/lib/i18n";

export type AdminUser = {
  id: string;
  name: string;
  username: string;
  email: string;
  roles: ("USER" | "ADMIN")[];
  enabled: boolean;
  createdAt: string;
};

export function UsersTable({ initial, currentUserId, t }: { initial: AdminUser[]; currentUserId: string; t: Dict }) {
  const [users, setUsers] = useState(initial);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function update(user: AdminUser, path: "roles" | "status", body: unknown) {
    setBusy(user.id);
    setError(null);
    const res = await fetch(`/api/backend/admin/users/${user.id}/${path}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    setBusy(null);
    if (res.ok) {
      const updated = (await res.json()) as AdminUser;
      setUsers((list) => list.map((u) => (u.id === updated.id ? updated : u)));
    } else {
      const problem = (await res.json().catch(() => ({}))) as { detail?: string };
      setError(problem.detail ?? t.common.error);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      {error && <Alert tone="danger">{error}</Alert>}
      <div className="overflow-x-auto rounded-2xl border border-border bg-surface">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="bg-surface-2 text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-4 py-3">{t.auth.name}</th>
              <th className="px-4 py-3">{t.auth.email}</th>
              <th className="px-4 py-3">{t.admin.roles}</th>
              <th className="px-4 py-3">{t.admin.status}</th>
              <th className="px-4 py-3 text-right" />
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {users.map((u) => {
              const admin = u.roles.includes("ADMIN");
              const self = u.id === currentUserId;
              return (
                <tr key={u.id}>
                  <td className="px-4 py-3">
                    <p className="font-semibold">{u.name}</p>
                    <p className="text-xs text-muted">@{u.username}</p>
                  </td>
                  <td className="px-4 py-3">{u.email}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1">
                      {u.roles.map((r) => (
                        <span
                          key={r}
                          className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${
                            r === "ADMIN" ? "bg-primary/15 text-primary" : "bg-surface-2 text-muted"
                          }`}
                        >
                          {r}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-semibold ${u.enabled ? "text-success" : "text-danger"}`}>
                      {u.enabled ? t.admin.active : t.admin.disabled}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-2">
                      <Button
                        variant="secondary"
                        disabled={busy === u.id || (self && admin)}
                        onClick={() => update(u, "roles", { roles: admin ? ["USER"] : ["USER", "ADMIN"] })}
                      >
                        {admin ? t.admin.removeAdmin : t.admin.makeAdmin}
                      </Button>
                      <Button
                        variant={u.enabled ? "ghost" : "secondary"}
                        disabled={busy === u.id || self}
                        onClick={() => update(u, "status", { enabled: !u.enabled })}
                      >
                        {u.enabled ? t.admin.disable : t.admin.enable}
                      </Button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
