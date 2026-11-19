import type { Tech } from "@/lib/techs";

/** Ícone de uma tecnologia em um selo no estilo NOVA; sem logo livre, mostra o monograma em Montserrat. */
export function TechIcon({ tech, size = 28 }: { tech: Pick<Tech, "name" | "icon">; size?: number }) {
  const inner = Math.round(size * 0.6);
  return (
    <span
      className="inline-flex shrink-0 items-center justify-center rounded-lg border border-border bg-surface"
      style={{ width: size, height: size }}
      title={tech.name}
    >
      {tech.icon ? (
        <svg viewBox="0 0 24 24" width={inner} height={inner} role="img" aria-label={tech.name}>
          <path d={tech.icon.path} fill={isDark(tech.icon.hex) ? "currentColor" : tech.icon.hex} className="text-text" />
        </svg>
      ) : (
        <span className="font-bold text-primary" style={{ fontSize: inner * 0.7 }} aria-label={tech.name}>
          {tech.name.slice(0, 2).toUpperCase()}
        </span>
      )}
    </span>
  );
}

/** Logos quase pretos (ex.: Next.js) usam a cor do texto para continuar visíveis no tema escuro. */
function isDark(hex: string): boolean {
  const n = parseInt(hex.slice(1), 16);
  const [r, g, b] = [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b < 40;
}
