"use client";

import { useState } from "react";
import { Alert, Button, Card } from "@/components/ui/ui";
import type { Comparison, VersionSummaryLike } from "./compare-types";

/** Máquina do tempo: escolha duas versões e veja o que mudou nos arquivos, nas rotas, nos dados e nas telas. */
export function CompareVersions({ projectId, versions }: { projectId: string; versions: VersionSummaryLike[] }) {
  const numbers = versions.map((v) => v.number).sort((a, b) => a - b);
  const [from, setFrom] = useState(numbers.length > 1 ? numbers[numbers.length - 2] : numbers[0]);
  const [to, setTo] = useState(numbers[numbers.length - 1]);
  const [result, setResult] = useState<Comparison | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function compare() {
    setError(null);
    const res = await fetch(`/api/backend/projects/${projectId}/compare?from=${from}&to=${to}`);
    const body = await res.json().catch(() => ({}));
    if (res.ok) setResult(body);
    else setError(body.detail ?? "Não foi possível comparar.");
  }

  const select = (label: string, value: number, onChange: (n: number) => void) => (
    <label className="flex items-center gap-2 text-sm font-semibold">{label}
      <select value={value} onChange={(e) => onChange(Number(e.target.value))} className="rounded-lg border border-border bg-surface px-2 py-1.5 text-sm">
        {numbers.map((n) => <option key={n} value={n}>v{n}</option>)}
      </select>
    </label>
  );

  return (
    <Card>
      <h2 className="font-bold">Máquina do tempo</h2>
      <p className="mb-3 text-sm text-muted">Compare duas versões: arquivos, rotas da API, entidades e telas que entraram ou saíram.</p>
      <div className="flex flex-wrap items-center gap-3">
        {select("De", from, setFrom)}
        {select("Para", to, setTo)}
        <Button variant="secondary" onClick={compare} disabled={from === to}>Comparar</Button>
      </div>
      {error && <div className="mt-3"><Alert tone="danger">{error}</Alert></div>}
      {result && (
        <div className="mt-4 flex flex-col gap-3">
          <div className="grid gap-2 text-xs sm:grid-cols-3">
            {([["Rotas", result.endpointsAdded, result.endpointsRemoved], ["Entidades", result.entitiesAdded, result.entitiesRemoved], ["Telas", result.screensAdded, result.screensRemoved]] as const).map(([label, added, removed]) => (
              <div key={label} className="rounded-lg border border-border p-2">
                <p className="font-bold">{label}</p>
                {added.length === 0 && removed.length === 0 && <p className="text-muted">sem mudança</p>}
                {added.map((x) => <p key={"+" + x} className="font-mono text-success">+ {x}</p>)}
                {removed.map((x) => <p key={"-" + x} className="font-mono text-danger">− {x}</p>)}
              </div>
            ))}
          </div>
          <p className="text-sm">{result.files.length} arquivos mudaram de v{result.from} para v{result.to}.</p>
          <ul className="flex flex-col gap-1.5">
            {result.files.map((f) => (
              <li key={f.path}>
                <details className="rounded-lg border border-border">
                  <summary className="flex cursor-pointer flex-wrap items-baseline gap-2 px-3 py-2 text-xs">
                    <span className="font-bold">{f.change === "ADDED" ? "NOVO" : f.change === "REMOVED" ? "REMOVIDO" : "ALTERADO"}</span>
                    <span className="break-all font-mono">{f.path}</span>
                    <span className="text-success">+{f.added}</span><span className="text-danger">−{f.removed}</span>
                  </summary>
                  <pre className="max-h-96 overflow-auto px-3 py-2 font-mono text-[11px] leading-relaxed">
                    {f.diff.split("\n").map((l, i) => (
                      <span key={i} className={`block ${l.startsWith("+ ") ? "bg-success/10 text-success" : l.startsWith("- ") ? "bg-danger/10 text-danger" : ""}`}>{l}</span>
                    ))}
                  </pre>
                </details>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}
