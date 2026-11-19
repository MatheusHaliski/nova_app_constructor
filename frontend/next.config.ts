import type { NextConfig } from "next";

// O NOVA é quase todo autenticado e dinâmico (sessão, tema e idioma vêm de cookies),
// por isso usa o modelo padrão de renderização dinâmica, sem cacheComponents.
const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
