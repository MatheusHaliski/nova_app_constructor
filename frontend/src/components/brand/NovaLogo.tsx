import { useId } from "react";

/*
 * Marca NOVA redesenhada em SVG a partir do logo do Trello (cartão "Objetivos"):
 * N azul → ciano, O em degradê ciano → verde → amarelo → laranja, losango com o "V" em fita
 * e "A" sem travessão (Λ) em azul. O slogan usa Montserrat, a fonte identificada no logo.
 */

type MarkProps = { size?: number; className?: string; title?: string };

/** Símbolo compacto: losango com o "V" em fita (barra lateral, favicon, avatar padrão). */
export function NovaMark({ size = 32, className, title = "NOVA" }: MarkProps) {
  const id = useId().replace(/:/g, "");
  return (
    <svg viewBox="340 -4 208 208" width={size} height={size} className={className} role="img" aria-label={title}>
      <Defs id={id} />
      <Diamond id={id} />
    </svg>
  );
}

type LogoProps = { height?: number; className?: string; withTagline?: boolean };

/** Letreiro completo "NOVA" (barra superior, telas de login e cadastro). */
export function NovaLogo({ height = 28, className, withTagline = false }: LogoProps) {
  const id = useId().replace(/:/g, "");
  const viewHeight = withTagline ? 268 : 218;
  return (
    <svg
      viewBox={`-6 -10 798 ${viewHeight}`}
      height={height}
      width={(height * 798) / viewHeight}
      className={className}
      role="img"
      aria-label="NOVA Software Application Constructor"
    >
      <Defs id={id} />
      {/* N */}
      <polygon
        points="0,180 0,20 40,20 112,118 112,20 150,20 150,180 110,180 38,82 38,180"
        fill={`url(#${id}-n)`}
      />
      {/* O */}
      <path
        fillRule="evenodd"
        fill={`url(#${id}-o)`}
        d="M255 20a83 80 0 1 0 0.01 0Z M255 58a45 42 0 1 1-0.01 0Z"
      />
      <Diamond id={id} />
      {/* Λ (A sem travessão) */}
      <polygon points="560,180 648,20 692,20 780,180 736,180 670,60 604,180" fill={`url(#${id}-a)`} />
      {withTagline && (
        <text
          x="0"
          y="246"
          textLength="780"
          lengthAdjust="spacingAndGlyphs"
          fontFamily="Montserrat Variable, Montserrat, sans-serif"
          fontWeight={400}
          fontSize="36"
          fill="currentColor"
        >
          Software Application Constructor
        </text>
      )}
    </svg>
  );
}

function Defs({ id }: { id: string }) {
  return (
    <defs>
      <linearGradient id={`${id}-n`} x1="0" x2="1" y1="0" y2="0">
        <stop offset="0" stopColor="#0b4dff" />
        <stop offset="1" stopColor="#00d2ff" />
      </linearGradient>
      <linearGradient id={`${id}-o`} x1="0" x2="1" y1="0" y2="0">
        <stop offset="0" stopColor="#0aa6ff" />
        <stop offset="0.35" stopColor="#14b8c8" />
        <stop offset="0.55" stopColor="#8cc63f" />
        <stop offset="0.75" stopColor="#f7d11e" />
        <stop offset="1" stopColor="#ff8a00" />
      </linearGradient>
      <linearGradient id={`${id}-d`} x1="0" x2="1" y1="1" y2="0">
        <stop offset="0" stopColor="#0b5cff" />
        <stop offset="0.35" stopColor="#14b8a6" />
        <stop offset="0.6" stopColor="#f7d11e" />
        <stop offset="1" stopColor="#ff8a00" />
      </linearGradient>
      <linearGradient id={`${id}-vl`} x1="0" x2="1" y1="0" y2="1">
        <stop offset="0" stopColor="#1d4ed8" />
        <stop offset="1" stopColor="#0f766e" />
      </linearGradient>
      <linearGradient id={`${id}-vr`} x1="0" x2="1" y1="0" y2="1">
        <stop offset="0" stopColor="#f7d11e" />
        <stop offset="1" stopColor="#ff6a00" />
      </linearGradient>
      <linearGradient id={`${id}-a`} x1="0" x2="1" y1="0" y2="0">
        <stop offset="0" stopColor="#00a3ff" />
        <stop offset="1" stopColor="#0033e6" />
      </linearGradient>
    </defs>
  );
}

function Diamond({ id }: { id: string }) {
  return (
    <g>
      <rect
        x="376"
        y="32"
        width="136"
        height="136"
        rx="14"
        transform="rotate(45 444 100)"
        fill="none"
        stroke={`url(#${id}-d)`}
        strokeWidth="13"
      />
      <polygon points="400,60 426,60 452,148 436,148" fill={`url(#${id}-vl)`} />
      <polygon points="414,60 430,60 449,126 441,136" fill="#22c55e" opacity="0.85" />
      <polygon points="462,60 488,60 452,148 436,148" fill={`url(#${id}-vr)`} />
    </g>
  );
}
