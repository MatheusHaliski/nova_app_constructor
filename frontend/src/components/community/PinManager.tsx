"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alert, Button, Card } from "@/components/ui/ui";
import type { CommunityCard } from "@/lib/community-types";

/** Escolha dos até 6 projetos fixados no portfólio (só os publicados na rede). */
export function PinManager({ projects, pinned }: { projects: CommunityCard[]; pinned: string[] }) {
  const router = useRouter();
  const [chosen, setChosen] = useState<string[]>(pinned);
  const [message, setMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  if (projects.length === 0) return null;
  async function save() {
    const res = await fetch("/api/backend/me/pinned-projects", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ projectIds: chosen }) });
    const body = await res.json().catch(() => ({}));
    setMessage(res.ok ? { tone: "success", text: "Portfólio atualizado." } : { tone: "danger", text: body.detail ?? "Não foi possível salvar." });
    if (res.ok) router.refresh();
  }
  return (
    <Card>
      <h2 className="font-bold">Fixar no portfólio</h2>
      <p className="mb-2 text-sm text-muted">Até 6 projetos, na ordem em que você marcar.</p>
      <ul className="flex flex-col gap-1">
        {projects.map((p) => {
          const on = chosen.includes(p.id);
          return (
            <li key={p.id}>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={on} disabled={!on && chosen.length >= 6}
                  onChange={() => setChosen(on ? chosen.filter((x) => x !== p.id) : [...chosen, p.id])} />
                {p.name}
              </label>
            </li>
          );
        })}
      </ul>
      {message && <div className="mt-2"><Alert tone={message.tone}>{message.text}</Alert></div>}
      <div className="mt-3"><Button onClick={save}>Salvar portfólio</Button></div>
    </Card>
  );
}
