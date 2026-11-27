/*
 * Runtime da prévia das telas do NOVA. Roda dentro de um iframe isolado (sandbox sem mesma origem): recebe o código do
 * frontend do projeto (com as edições manuais) e respostas de exemplo da API, transpila com o sucrase, executa as páginas
 * e intercepta o fetch. Cada elemento leva data-nova-src="arquivo:linha" para a edição direta na prévia.
 */
import * as React from "react";
import { createRoot } from "react-dom/client";
import { transform } from "sucrase";

const host = { root: null, state: null, cache: new Map(), styles: [] };

function post(message) {
  window.parent.postMessage(message, "*");
}

// ---------------------------------------------------------------- elementos com origem no código

function h(type, props, ...children) {
  if (props && (props.__source || props.__self)) {
    const { __source, __self, ...rest } = props;
    if (typeof type === "string" && __source) {
      rest["data-nova-src"] = `${__source.fileName}:${__source.lineNumber}`;
    }
    props = rest;
  }
  return React.createElement(type, props, ...children);
}

// ---------------------------------------------------------------- módulos do Next simulados

class Redirect extends Error {
  constructor(href) {
    super("redirect");
    this.href = href;
  }
}
class NotFound extends Error {}

function navigate(href) {
  if (!href || /^https?:/.test(href)) return;
  post({ type: "nova:navigate", href });
  host.state.route = href;
  draw();
}

const Link = React.forwardRef(function Link({ href, children, prefetch, replace, scroll, ...rest }, ref) {
  const target = typeof href === "string" ? href : href?.pathname ?? "/";
  return React.createElement("a", { ...rest, ref, href: target, onClick: (e) => { e.preventDefault(); navigate(target); } }, children);
});

const nextModules = {
  react: React,
  "react/jsx-runtime": { jsx: React.createElement, jsxs: React.createElement, Fragment: React.Fragment },
  "next/link": { __esModule: true, default: Link },
  "next/image": { __esModule: true, default: (p) => React.createElement("img", { ...p, fill: undefined, priority: undefined }) },
  "next/navigation": {
    redirect: (href) => { throw new Redirect(href); },
    permanentRedirect: (href) => { throw new Redirect(href); },
    notFound: () => { throw new NotFound(); },
    useRouter: () => ({ push: navigate, replace: navigate, refresh: () => draw(), back: () => {}, prefetch: () => {} }),
    usePathname: () => host.state.route.split("?")[0],
    useSearchParams: () => new URLSearchParams(host.state.route.split("?")[1] ?? ""),
    useParams: () => host.state.params ?? {},
  },
  "next/font/google": new Proxy({}, { get: (_, name) => () => ({ className: "", style: { fontFamily: String(name).replace(/_/g, " ") } }) }),
  "next/font/local": { __esModule: true, default: () => ({ className: "", style: {} }) },
  "next/headers": { cookies: async () => ({ get: () => undefined }), headers: async () => new Headers() },
  "next/cache": { revalidatePath: () => {}, revalidateTag: () => {} },
  next: {},
};

// ---------------------------------------------------------------- módulos do projeto

function resolve(from, spec) {
  const root = host.state.root;
  let base;
  if (spec.startsWith("@/")) base = root + "src/" + spec.slice(2);
  else if (spec.startsWith("./") || spec.startsWith("../")) {
    const parts = from.split("/").slice(0, -1);
    for (const p of spec.split("/")) {
      if (p === "..") parts.pop();
      else if (p !== ".") parts.push(p);
    }
    base = parts.join("/");
  } else return null;
  for (const c of [base, base + ".tsx", base + ".ts", base + ".jsx", base + ".js", base + "/index.tsx", base + "/index.ts", base + "/index.js"]) {
    if (host.state.sources[c] !== undefined) return c;
  }
  return base;
}

function load(path) {
  if (host.cache.has(path)) return host.cache.get(path).exports;
  const source = host.state.sources[path];
  if (source === undefined) throw new Error("Arquivo não encontrado na prévia: " + path);
  const module = { exports: {} };
  host.cache.set(path, module);
  if (path.endsWith(".css")) {
    host.styles.push(source.replace(/@import\s+["']tailwindcss[^;]*;/g, "").replace(/@(plugin|source|theme)[^;{]*;/g, ""));
    return module.exports;
  }
  if (path.endsWith(".json")) {
    module.exports = JSON.parse(source);
    return module.exports;
  }
  const code = transform(source, {
    transforms: ["typescript", "jsx", "imports"],
    jsxRuntime: "classic",
    jsxPragma: "__h",
    jsxFragmentPragma: "__Fragment",
    production: false,
    filePath: path,
  }).code.replace(/^\s*["']use (client|server)["'];?/gm, "");
  const require = (spec) => {
    if (nextModules[spec]) return nextModules[spec];
    const target = resolve(path, spec);
    if (target) return load(target);
    if (spec.endsWith(".css")) return {};
    throw new Error("Pacote não disponível na prévia: " + spec);
  };
  const run = new Function("require", "module", "exports", "__h", "__Fragment", "process", code);
  run.call(undefined, require, module, module.exports, h, React.Fragment, { env: { NODE_ENV: "development", API_URL: "http://api.preview" } });
  return module.exports;
}

// ---------------------------------------------------------------- API simulada

function matchPath(pattern, path) {
  const a = pattern.split("/");
  const b = path.split("/");
  if (a.length !== b.length) return false;
  return a.every((seg, i) => /^\{.+\}$/.test(seg) || /^\[.+\]$/.test(seg) || /^:.+/.test(seg) || seg === b[i]);
}

window.fetch = async (input, init = {}) => {
  const url = new URL(typeof input === "string" ? input : input.url, "http://api.preview");
  const method = (init.method || "GET").toUpperCase();
  const { mocks, view, role } = host.state;
  const mock = mocks.find((m) => m.method === method && matchPath(m.path, url.pathname));
  const respond = (status, body) => new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
  post({ type: "nova:request", method, path: url.pathname, status: mock ? 200 : method === "GET" ? 404 : 201 });
  if (view === "loading") return new Promise(() => {});
  if (view === "slow") await new Promise((r) => setTimeout(r, 1500));
  if (view === "error") return respond(500, { title: "Erro simulado na prévia" });
  if (mock && role === "visitor" && mock.access && mock.access !== "PUBLIC") return respond(401, { title: "Não autenticado" });
  if (method !== "GET") return role === "visitor" ? respond(401, { title: "Não autenticado" }) : respond(201, init.body ? JSON.parse(init.body) : {});
  if (!mock) return respond(404, { title: "Sem resposta de exemplo para " + url.pathname });
  if (view === "empty" && Array.isArray(mock.body)) return respond(200, []);
  if (view === "many" && Array.isArray(mock.body) && mock.body.length) {
    // Muitos itens: repete os exemplos até 30 linhas, com ids diferentes, para ver rolagem e paginação.
    return respond(200, Array.from({ length: 30 }, (_, i) => ({ ...mock.body[i % mock.body.length], id: i + 1 })));
  }
  if (view === "long") return respond(200, lengthen(mock.body));
  return respond(200, mock.body);
};

/** Textos longos: estica cada texto para ver quebras de linha e cortes. */
function lengthen(value) {
  if (Array.isArray(value)) return value.map(lengthen);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, lengthen(v)]));
  if (typeof value === "string" && !/^\d{4}-\d{2}-\d{2}/.test(value) && !/^[A-Z_]+$/.test(value)) {
    return value + " — com um texto bem mais comprido do que o esperado, para conferir se a tela quebra a linha sem estourar o layout";
  }
  return value;
}

// ---------------------------------------------------------------- telas

function findPage(route) {
  const path = route.split("?")[0].replace(/\/$/, "") || "/";
  for (const page of host.state.pages) {
    if (matchPath(page.route, path)) {
      const params = {};
      page.route.split("/").forEach((seg, i) => {
        const m = /^\[(.+)\]$/.exec(seg);
        if (m) params[m[1]] = path.split("/")[i];
      });
      return { page, params };
    }
  }
  return null;
}

function unwrapHtml(el) {
  if (!el || typeof el !== "object") return el;
  if (el.type === "html") {
    const kids = React.Children.toArray(el.props.children);
    const body = kids.find((k) => k && k.type === "body");
    return body ? React.createElement(React.Fragment, null, body.props.children) : el.props.children;
  }
  return el;
}

function message(title, text) {
  return h("div", { style: { padding: 32, fontFamily: "system-ui, sans-serif", color: "#334155" } }, h("h2", null, title), h("p", null, text));
}

/** React com Vite: monta o App do projeto (src/App.tsx) com a rota definida pela prévia; o roteador gerado lê __NOVA_ROUTE__. */
function drawVite() {
  const state = host.state;
  const root = state.root;
  const css = Object.keys(state.sources).filter((p) => p.startsWith(root + "src/") && p.endsWith(".css"));
  css.forEach(load);
  const appPath = [root + "src/App.tsx", root + "src/App.jsx"].find((p) => state.sources[p] !== undefined);
  if (!appPath) return message("Sem App", "O projeto não tem src/App.tsx.");
  const mod = load(appPath);
  const App = mod.App ?? mod.default;
  return h(App, null);
}

window.addEventListener("popstate", () => {
  if (host.state && host.state.framework === "react-vite" && window.__NOVA_ROUTE__ !== undefined) {
    host.state.route = window.__NOVA_ROUTE__;
    post({ type: "nova:navigate", href: window.__NOVA_ROUTE__ });
  }
});

async function draw() {
  const state = host.state;
  host.cache.clear();
  host.styles = [];
  let element;
  if (state.framework === "react-vite") {
    window.__NOVA_ROUTE__ = state.route;
    try {
      element = drawVite();
    } catch (e) {
      element = message("A prévia não conseguiu montar esta tela", String(e && e.message ? e.message : e));
      post({ type: "nova:error", message: String(e && e.message ? e.message : e) });
    }
    return mount(element);
  }
  try {
    const found = findPage(state.route);
    if (!found) {
      element = message("Página não encontrada", "Nenhuma página do projeto responde em " + state.route + ".");
    } else {
      state.params = found.params;
      const layoutPath = Object.keys(state.sources).find((p) => p === state.root + "src/app/layout.tsx" || p === state.root + "app/layout.tsx");
      for (const css of Object.keys(state.sources).filter((p) => p.endsWith("globals.css"))) load(css);
      const mod = load(found.page.file);
      const Page = mod.default;
      const query = Object.fromEntries(new URLSearchParams(state.route.split("?")[1] ?? ""));
      if (state.view === "loading") {
        element = message("Carregando…", "Simulando a API demorando para responder.");
      } else {
        let page = Page({ params: Promise.resolve(found.params), searchParams: Promise.resolve(query) });
        if (page && typeof page.then === "function") page = await page;
        if (layoutPath) {
          const Layout = load(layoutPath).default;
          let wrapped = Layout({ children: page });
          if (wrapped && typeof wrapped.then === "function") wrapped = await wrapped;
          element = unwrapHtml(wrapped);
        } else {
          element = page;
        }
      }
    }
  } catch (e) {
    if (e instanceof Redirect) {
      post({ type: "nova:navigate", href: e.href });
      state.route = e.href;
      return draw();
    }
    element = e instanceof NotFound ? message("Não encontrado (404)", "A página chamou notFound().")
      : message("A prévia não conseguiu montar esta tela", String(e && e.message ? e.message : e));
    post({ type: "nova:error", message: String(e && e.message ? e.message : e) });
  }
  mount(element);
}

function mount(element) {
  let style = document.getElementById("nova-styles");
  if (!style) {
    style = document.createElement("style");
    style.id = "nova-styles";
    document.head.appendChild(style);
  }
  style.textContent = host.styles.join("\n");
  if (!host.root) host.root = createRoot(document.getElementById("root"));
  host.root.render(element);
  post({ type: "nova:rendered", route: host.state.route, height: document.body.scrollHeight });
}

// ---------------------------------------------------------------- edição direta

function sourceOf(target) {
  const el = target.closest ? target.closest("[data-nova-src]") : null;
  if (!el) return null;
  const raw = el.getAttribute("data-nova-src");
  const i = raw.lastIndexOf(":");
  return { el, file: raw.slice(0, i), line: Number(raw.slice(i + 1)) };
}

document.addEventListener("click", (e) => {
  if (!host.state || !host.state.editing) return;
  const src = sourceOf(e.target);
  if (!src) return;
  e.preventDefault();
  e.stopPropagation();
  document.querySelectorAll("[data-nova-selected]").forEach((x) => x.removeAttribute("data-nova-selected"));
  src.el.setAttribute("data-nova-selected", "");
  post({ type: "nova:select", file: src.file, line: src.line, tag: src.el.tagName.toLowerCase(), text: src.el.textContent.slice(0, 200) });
}, true);

document.addEventListener("dblclick", (e) => {
  if (!host.state || !host.state.editing) return;
  const src = sourceOf(e.target);
  if (!src || src.el.children.length > 0) return;
  const before = src.el.textContent;
  src.el.contentEditable = "true";
  src.el.focus();
  // Enter confirma, Esc desfaz; sair do elemento também confirma.
  const onKey = (ev) => {
    if (ev.key === "Enter") {
      ev.preventDefault();
      src.el.blur();
    } else if (ev.key === "Escape") {
      src.el.textContent = before;
      src.el.blur();
    }
  };
  src.el.addEventListener("keydown", onKey);
  src.el.addEventListener("blur", () => {
    src.el.removeEventListener("keydown", onKey);
    src.el.contentEditable = "false";
    const after = src.el.textContent;
    if (after !== before) post({ type: "nova:edit", file: src.file, line: src.line, before, after });
  }, { once: true });
});

window.addEventListener("message", (e) => {
  const data = e.data;
  if (!data || data.type !== "nova:render") return;
  host.state = { ...data };
  draw();
});

const css = document.createElement("style");
css.textContent = "[data-nova-editing] [data-nova-src]:hover{outline:2px dashed #1554f0;outline-offset:2px;cursor:pointer}[data-nova-selected]{outline:2px solid #1554f0!important;outline-offset:2px}";
document.head.appendChild(css);
post({ type: "nova:ready" });
