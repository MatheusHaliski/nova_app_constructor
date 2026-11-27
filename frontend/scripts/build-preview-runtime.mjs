// Empacota o runtime da prévia das telas (React, ReactDOM e sucrase) em public/preview/runtime.js.
import { build } from "esbuild";

await build({
  entryPoints: ["scripts/preview-runtime/runtime.js"],
  bundle: true,
  minify: true,
  format: "iife",
  platform: "browser",
  target: "es2022",
  outfile: "public/preview/runtime.js",
  define: { "process.env.NODE_ENV": '"production"' },
  legalComments: "none",
  logLevel: "warning",
});
console.log("public/preview/runtime.js pronto");
