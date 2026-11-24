"use client";

import Link from "next/link";
import { useState } from "react";
import { formatDate } from "@/components/projects/format";
import { Alert, Button, Card, Input } from "@/components/ui/ui";
import { REPORT_REASONS, type Report } from "@/lib/community-types";
import { call, errorText } from "./client";

/** Fila de moderação: cada denúncia pode ser descartada, ocultar o comentário ou despublicar o projeto (o dono é avisado). */
export function ReportsTable({ initial, resolved }: { initial: Report[]; resolved: boolean }) {
  const [reports, setReports] = useState(initial);
  const [notes, setNotes] = useState<Record<number, string>>({});
  const [error, setError] = useState<string | null>(null);

  async function resolve(id: number, action: "DISMISS" | "UNPUBLISH" | "HIDE_COMMENT") {
    const result = await call(`/api/backend/admin/reports/${id}/resolve`, "POST", { action, note: notes[id] ?? "" });
    if (result.ok) {
      setReports(reports.filter((r) => r.id !== id));
      setError(null);
    } else setError(errorText(result.problem));
  }

  if (reports.length === 0) return <Card className="py-10 text-center text-sm text-muted">{resolved ? "Nenhuma denúncia resolvida." : "Nenhuma denúncia aberta."}</Card>;
  return (
    <div className="flex flex-col gap-3">
      {error && <Alert tone="danger">{error}</Alert>}
      {reports.map((r) => (
        <Card key={r.id}>
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div className="min-w-0">
              <p className="text-sm font-bold">
                {REPORT_REASONS[r.reason]} · {r.commentId ? "comentário" : "projeto"}
              </p>
              <p className="text-xs text-muted">
                <Link href={`/comunidade/${r.projectId}`} className="font-semibold text-primary">
                  {r.projectName}
                </Link>{" "}
                de @{r.projectAuthor} · denunciado por {r.reporter ? `@${r.reporter}` : "usuário removido"} em {formatDate(r.createdAt)}
              </p>
            </div>
            {r.resolution && <span className="rounded-full bg-surface-2 px-2 py-0.5 text-xs font-semibold text-muted">{r.resolution}</span>}
          </div>
          {r.commentBody && <blockquote className="mt-2 border-l-2 border-border pl-3 text-sm">{r.commentBody}</blockquote>}
          {r.details && <p className="mt-2 text-sm text-muted">Detalhes: {r.details}</p>}
          {!resolved && (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <Input
                aria-label={`Motivo da decisão da denúncia ${r.id}`}
                placeholder="Motivo da decisão (vai para o dono)"
                maxLength={300}
                value={notes[r.id] ?? ""}
                onChange={(e) => setNotes({ ...notes, [r.id]: e.target.value })}
                className="min-w-0 max-w-sm flex-1"
              />
              <Button variant="secondary" onClick={() => resolve(r.id, "DISMISS")}>
                Descartar
              </Button>
              {r.commentId && (
                <Button variant="secondary" onClick={() => resolve(r.id, "HIDE_COMMENT")}>
                  Ocultar comentário
                </Button>
              )}
              <Button variant="danger" onClick={() => resolve(r.id, "UNPUBLISH")}>
                Despublicar projeto
              </Button>
            </div>
          )}
        </Card>
      ))}
    </div>
  );
}
