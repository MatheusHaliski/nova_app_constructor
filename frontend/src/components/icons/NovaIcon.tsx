/*
 * Ícones do NOVA, desenhados na mesma linguagem geométrica do logo (Montserrat, losango e fita do "V"):
 * grade 24×24, traço 2 arredondado e, quando ativos, o degradê da marca (ver <NovaIconGradient />).
 */

const PATHS = {
  home: "M4 11 12 4l8 7M6 9.5V20h4.5v-5h3v5H18V9.5",
  sparkle: "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8Z M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8Z",
  form: "M5 3h14v18H5Z M8 8h8 M8 12h8 M8 16h5",
  copilot: "M12 2.5 21.5 12 12 21.5 2.5 12Z M8.5 9.5l3.5 6 3.5-6",
  assist: "M4 5h16v11H9l-5 4Z M8 9.5h8 M8 12.5h5",
  screens: "M3 5h13v10H3Z M18 8h3v11h-7v-2 M7 19h6",
  backlog: "M4 6h2 M9 6h11 M4 12h2 M9 12h11 M4 18h2 M9 18h11",
  diagram: "M3 4h6v5H3Z M15 15h6v5h-6Z M15 4h6v5h-6Z M6 9v3h12V9 M18 12v3",
  folder: "M3 6.5h6.5l2 2.5H21v10H3Z",
  tree: "M5 3v14h5 M5 8h5 M10 5.5h9v5h-9Z M10 14.5h9v5h-9Z",
  history: "M3.5 12a8.5 8.5 0 1 0 2.6-6.1 M3 4v5h5 M12 8v4.5l3 2",
  download: "M12 3v12 M7 10.5l5 5 5-5 M4 20h16",
  git: "M6 3.5a2.5 2.5 0 1 0 .01 0Z M6 15.5a2.5 2.5 0 1 0 .01 0Z M18 3.5a2.5 2.5 0 1 0 .01 0Z M6 8.5v7 M18 8.5c0 4.5-6 4.5-11 8",
  shieldCheck: "M12 3 20 6v6c0 4.5-3.5 7.8-8 9-4.5-1.2-8-4.5-8-9V6Z M8.5 12l2.5 2.5 4.5-5",
  rocket: "M12 15 9 12c1.5-5 5-8.5 11-9-0.5 6-4 9.5-9 11Z M9 12l-4 1 2-4h4 M12 15l-1 4 4-2v-4 M6 18l-2 2",
  feed: "M4 4h16v6H4Z M4 14h7v6H4Z M14 14h6 M14 17h6 M14 20h4",
  compass: "M12 3a9 9 0 1 0 0.01 0Z M15.5 8.5 13.5 13.5 8.5 15.5 10.5 10.5Z",
  share: "M18 3a3 3 0 1 0 0.01 0Z M6 9a3 3 0 1 0 0.01 0Z M18 15a3 3 0 1 0 0.01 0Z M8.7 10.7l6.6-3.4 M8.7 13.3l6.6 3.4",
  star: "M12 3.5l2.6 5.6 6 .7-4.5 4.1 1.2 6-5.3-3-5.3 3 1.2-6L3.4 9.8l6-.7Z",
  userCircle: "M12 3a9 9 0 1 0 0.01 0Z M12 7.5a3 3 0 1 0 0.01 0Z M6.5 18c1.2-2.3 3.2-3.5 5.5-3.5s4.3 1.2 5.5 3.5",
  layers: "M12 3 21 8 12 13 3 8Z M3 12.5l9 5 9-5 M3 16.5l9 5 9-5",
  database: "M12 3c4.4 0 8 1.3 8 3s-3.6 3-8 3-8-1.3-8-3 3.6-3 8-3Z M4 6v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6 M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3",
  api: "M8 6 3 12l5 6 M16 6l5 6-5 6 M13.5 4l-3 16",
  services: "M4 4h6v6H4Z M14 14h6v6h-6Z M10 7h4a3 3 0 0 1 3 3v4 M7 10v4a3 3 0 0 0 3 3h4",
  plug: "M9 3v5 M15 3v5 M6 8h12v3a6 6 0 0 1-12 0Z M12 17v4",
  lock: "M5 11h14v10H5Z M8 11V7.5a4 4 0 0 1 8 0V11 M12 15v2",
  sliders: "M4 6h9 M17 6h3 M15 4v4 M4 12h3 M11 12h9 M9 10v4 M4 18h11 M19 18h1 M17 16v4",
  layout: "M3 4h18v16H3Z M3 9h18 M9 9v11",
  book: "M4 4.5C6.5 3.5 9.5 3.5 12 5c2.5-1.5 5.5-1.5 8-.5V19c-2.5-1-5.5-1-8 .5-2.5-1.5-5.5-1.5-8-.5Z M12 5v14.5",
  chip: "M7 7h10v10H7Z M10 10h4v4h-4Z M9.5 3v4 M14.5 3v4 M9.5 17v4 M14.5 17v4 M3 9.5h4 M3 14.5h4 M17 9.5h4 M17 14.5h4",
  graduation: "M2.5 9 12 4.5 21.5 9 12 13.5Z M6.5 11v4.5c1.5 1.5 3.5 2.3 5.5 2.3s4-.8 5.5-2.3V11 M21.5 9v5",
  shield: "M12 3 20 6v6c0 4.5-3.5 7.8-8 9-4.5-1.2-8-4.5-8-9V6Z",
  server: "M4 4h16v6H4Z M4 14h16v6H4Z M7.5 7h.01 M7.5 17h.01 M11 7h6 M11 17h6",
  bell: "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15Z M10 20.5a2.2 2.2 0 0 0 4 0",
  user: "M12 4a4 4 0 1 0 0.01 0Z M4.5 20.5c1.3-3.6 4.2-5.5 7.5-5.5s6.2 1.9 7.5 5.5",
  logout: "M14 4h5v16h-5 M10 8l-4 4 4 4 M6 12h10",
  eyeOff: "M3 3l18 18 M10.6 6.2A9.8 9.8 0 0 1 12 6c5 0 8.5 4.5 9.5 6-.4.7-1.4 2.1-2.9 3.4 M6.5 7.6C4.5 9 3.1 10.9 2.5 12c1 1.5 4.5 6 9.5 6 1.6 0 3-.4 4.3-1.1 M9.9 9.9a3 3 0 0 0 4.2 4.2",
  moon: "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z",
  globe: "M12 3a9 9 0 1 0 0.01 0Z M3 12h18 M12 3c2.5 2.6 3.7 5.6 3.7 9s-1.2 6.4-3.7 9c-2.5-2.6-3.7-5.6-3.7-9S9.5 5.6 12 3Z",
  dataExport: "M12 3c4 0 7 1.1 7 2.5S16 8 12 8 5 6.9 5 5.5 8 3 12 3Z M5 5.5v8c0 1.2 2.2 2.2 5.2 2.5 M19 5.5V11 M17 14v7 M14 18l3 3 3-3",
  trash: "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13 M10 11v6 M14 11v6",
  menu: "M4 7h16 M4 12h16 M4 17h16",
  chevronDown: "M6 9l6 6 6-6",
  chevronLeft: "M15 6l-6 6 6 6",
  chevronRight: "M9 6l6 6-6 6",
  search: "M10.5 4a6.5 6.5 0 1 0 0.01 0Z M15.5 15.5 20 20",
  close: "M6 6l12 12 M18 6 6 18",
  check: "M5 12.5 10 17 19 7",
  info: "M12 3a9 9 0 1 0 0.01 0Z M12 11v5 M12 7.5h.01",
  alert: "M12 3.5 21.5 20h-19Z M12 10v4.5 M12 17.5h.01",
  key: "M8 11a4 4 0 1 0 0.01 0Z M11 14.5 20 6 M16.5 9.5l2 2 M14.5 11.5l1.5 1.5",
  mail: "M3 5h18v14H3Z M3 6l9 7 9-7",
  calendar: "M4 5h16v15H4Z M4 9.5h16 M8 3v4 M16 3v4",
  construction: "M12 2.5 21.5 12 12 21.5 2.5 12Z M8 12h8 M12 8v8",
} as const;

export type NovaIconName = keyof typeof PATHS;

type Props = {
  name: NovaIconName;
  size?: number;
  className?: string;
  /** Usa o degradê da marca no traço (itens ativos). */
  gradient?: boolean;
  title?: string;
};

export function NovaIcon({ name, size = 20, className, gradient = false, title }: Props) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke={gradient ? "url(#nova-icon-gradient)" : "currentColor"}
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      role={title ? "img" : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

/** Degradê usado pelos ícones ativos; renderizado uma vez no layout. */
export function NovaIconGradient() {
  return (
    <svg width="0" height="0" aria-hidden="true" style={{ position: "absolute" }}>
      <defs>
        <linearGradient id="nova-icon-gradient" x1="0" y1="24" x2="24" y2="0" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#0b4dff" />
          <stop offset="0.35" stopColor="#00c8ff" />
          <stop offset="0.6" stopColor="#8cc63f" />
          <stop offset="0.8" stopColor="#f7d11e" />
          <stop offset="1" stopColor="#ff8a00" />
        </linearGradient>
      </defs>
    </svg>
  );
}
