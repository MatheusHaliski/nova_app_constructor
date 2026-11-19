# NOVA · Frontend

Next.js 16 (App Router) + React 19 + TypeScript + Tailwind CSS. Veja o [README principal](../README.md).

- `src/proxy.ts` — controle de acesso e renovação de sessão antes de renderizar.
- `src/app/api/*` — login, cadastro, saída, preferências e a ponte autenticada `/api/backend/*` (tokens em cookies httpOnly).
- `src/components/brand` — logo e símbolo NOVA em SVG; `src/components/icons` — ícones no estilo da marca.
- `src/lib/nav.ts` — barra lateral com os grupos de RF do Trello e a fase de cada um.
