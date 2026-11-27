package com.nova.constructor.insight;

import com.nova.constructor.insight.Model.FieldKind;
import com.nova.constructor.insight.Model.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Código que as melhorias escrevem num backend Spring Boot e numa página Next.js. Os testes gerados usam MockMvc em modo
 * standalone com dublês do Mockito, então rodam sem banco de dados e sem subir a aplicação.
 */
final class SpringCode {

    private SpringCode() {}

    /**
     * Campo usado pelos modelos de código.
     *
     * @param enumRef     como o código se refere ao enum (ex.: {@code Pedido.Status}); só para ENUM
     * @param statusField campo de status: fica fora do corpo de criar e editar, muda só pelo endpoint de status
     * @param primitive   o setter da entidade recebe um tipo primitivo
     */
    record F(String name, FieldKind kind, String javaType, boolean required, String enumRef, List<String> values, boolean statusField,
             boolean primitive) {

        String pascal() {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        String column() {
            return Names.snake(name);
        }

        /** Tipo no corpo da requisição: enums chegam como texto, primitivos como wrapper. */
        String requestType() {
            if (kind == FieldKind.ENUM) {
                return "String";
            }
            return switch (javaType) {
                case "int", "short" -> "Integer";
                case "long" -> "Long";
                case "double", "float" -> "Double";
                case "boolean" -> "Boolean";
                default -> javaType;
            };
        }
    }

    static String javaType(FieldKind kind) {
        return switch (kind) {
            case INTEGER -> "Integer";
            case LONG -> "Long";
            case DECIMAL -> "BigDecimal";
            case DOUBLE -> "Double";
            case BOOLEAN -> "Boolean";
            case DATE -> "LocalDate";
            case DATETIME -> "LocalDateTime";
            case INSTANT -> "Instant";
            case UUID -> "UUID";
            default -> "String";
        };
    }

    private static void typeImports(Set<String> imports, List<F> fields) {
        for (F f : fields) {
            switch (f.requestType()) {
                case "BigDecimal" -> imports.add("java.math.BigDecimal");
                case "LocalDate" -> imports.add("java.time.LocalDate");
                case "LocalDateTime" -> imports.add("java.time.LocalDateTime");
                case "Instant" -> imports.add("java.time.Instant");
                case "UUID" -> imports.add("java.util.UUID");
                default -> { }
            }
        }
    }

    private static String header(String pkg, Set<String> imports) {
        StringBuilder sb = new StringBuilder("package ").append(pkg).append(";\n\n");
        List<String> statics = imports.stream().filter(i -> i.startsWith("static ")).sorted().toList();
        statics.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!statics.isEmpty()) {
            sb.append('\n');
        }
        imports.stream().filter(i -> !i.startsWith("static ")).sorted().forEach(i -> sb.append("import ").append(i).append(";\n"));
        return sb.append('\n').toString();
    }

    // ---------------- entidade e repositório novos ----------------

    static String entity(String pkg, String entity, String table, List<F> fields, boolean jpa) {
        Set<String> imports = new TreeSet<>();
        typeImports(imports, fields.stream().map(f -> new F(f.name(), f.kind(), f.javaType(), f.required(), null, null, false, false)).toList());
        boolean hasEnum = fields.stream().anyMatch(f -> f.kind() == FieldKind.ENUM);
        if (jpa) {
            imports.addAll(List.of("jakarta.persistence.Column", "jakarta.persistence.Entity", "jakarta.persistence.GeneratedValue",
                    "jakarta.persistence.GenerationType", "jakarta.persistence.Id", "jakarta.persistence.Table"));
            if (hasEnum) {
                imports.addAll(List.of("jakarta.persistence.EnumType", "jakarta.persistence.Enumerated", "org.hibernate.annotations.JdbcTypeCode",
                        "org.hibernate.type.SqlTypes"));
            }
        }
        StringBuilder sb = new StringBuilder(header(pkg, imports));
        sb.append("/** ").append(entity).append(": dados guardados pelo app. Gerado pelo NOVA a partir da aba Melhorias. */\n");
        if (jpa) {
            sb.append("@Entity\n@Table(name = \"").append(table).append("\")\n");
        }
        sb.append("public class ").append(entity).append(" {\n\n");
        if (jpa) {
            sb.append("    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        }
        sb.append("    private Long id;\n");
        for (F f : fields) {
            sb.append('\n');
            String type = f.kind() == FieldKind.ENUM ? f.enumRef() : f.javaType();
            if (jpa) {
                if (f.kind() == FieldKind.ENUM) {
                    sb.append("    @Enumerated(EnumType.STRING)\n    @JdbcTypeCode(SqlTypes.VARCHAR)\n");
                }
                sb.append("    @Column(name = \"").append(f.column()).append("\"").append(f.required() ? ", nullable = false" : "");
                if (f.kind() == FieldKind.TEXT) {
                    sb.append(", length = 255");
                } else if (f.kind() == FieldKind.LONG_TEXT) {
                    sb.append(", length = 4000");
                } else if (f.kind() == FieldKind.ENUM) {
                    sb.append(", length = 30");
                } else if (f.kind() == FieldKind.DECIMAL) {
                    sb.append(", precision = 12, scale = 2");
                }
                sb.append(")\n");
            }
            sb.append("    private ").append(type).append(' ').append(f.name());
            if (f.kind() == FieldKind.ENUM && f.values() != null && !f.values().isEmpty()) {
                sb.append(" = ").append(f.enumRef()).append('.').append(f.values().getFirst());
            }
            sb.append(";\n");
        }
        for (F f : fields) {
            if (f.kind() == FieldKind.ENUM && f.enumRef().startsWith(entity + ".")) {
                String enumName = f.enumRef().substring(entity.length() + 1);
                sb.append("\n    /** Valores possíveis de ").append(f.name()).append(". */\n    public enum ").append(enumName).append(" {\n        ")
                        .append(String.join(", ", f.values())).append("\n    }\n");
            }
        }
        sb.append("\n    public Long getId() {\n        return id;\n    }\n\n    public void setId(Long id) {\n        this.id = id;\n    }\n");
        for (F f : fields) {
            String type = f.kind() == FieldKind.ENUM ? f.enumRef() : f.javaType();
            sb.append("\n    public ").append(type).append(" get").append(f.pascal()).append("() {\n        return ").append(f.name()).append(";\n    }\n\n")
                    .append("    public void set").append(f.pascal()).append('(').append(type).append(' ').append(f.name()).append(") {\n        this.")
                    .append(f.name()).append(" = ").append(f.name()).append(";\n    }\n");
        }
        return sb.append("}\n").toString();
    }

    static String repository(String pkg, String entity, boolean jpa) {
        if (jpa) {
            return header(pkg, Set.of("org.springframework.data.jpa.repository.JpaRepository"))
                    + "/** Acesso ao banco para " + entity + ". */\npublic interface " + entity + "Repository extends JpaRepository<" + entity + ", Long> {\n}\n";
        }
        return header(pkg, new TreeSet<>(Set.of("java.util.ArrayList", "java.util.List", "java.util.Map", "java.util.Optional",
                "java.util.concurrent.ConcurrentHashMap", "java.util.concurrent.atomic.AtomicLong", "org.springframework.stereotype.Repository")))
                + "/**\n * Guarda " + entity + " em memória (o projeto não tem banco configurado). Os dados somem quando a aplicação reinicia.\n"
                + " * Os métodos têm os mesmos nomes do Spring Data, para trocar por um JpaRepository sem mudar o controller.\n */\n"
                + "@Repository\npublic class " + entity + "Repository {\n\n"
                + "    private final Map<Long, " + entity + "> itens = new ConcurrentHashMap<>();\n"
                + "    private final AtomicLong sequencia = new AtomicLong();\n\n"
                + "    public List<" + entity + "> findAll() {\n        return new ArrayList<>(itens.values());\n    }\n\n"
                + "    public Optional<" + entity + "> findById(Long id) {\n        return Optional.ofNullable(itens.get(id));\n    }\n\n"
                + "    public " + entity + " save(" + entity + " item) {\n        if (item.getId() == null) {\n            item.setId(sequencia.incrementAndGet());\n        }\n"
                + "        itens.put(item.getId(), item);\n        return item;\n    }\n\n"
                + "    public void deleteById(Long id) {\n        itens.remove(id);\n    }\n}\n";
    }

    // ---------------- controller CRUD ----------------

    /**
     * @param extraImports imports da entidade e do repositório quando estão em outro pacote
     */
    static String controller(String pkg, String className, String entity, String path, Set<Operation> ops, List<F> fields, String idType,
                             Set<String> extraImports) {
        List<F> body = fields.stream().filter(f -> !f.statusField()).toList();
        boolean writes = ops.contains(Operation.CREATE) || ops.contains(Operation.UPDATE);
        boolean find = ops.contains(Operation.READ) || ops.contains(Operation.UPDATE) || ops.contains(Operation.DELETE);
        Set<String> imports = new TreeSet<>(extraImports);
        imports.addAll(List.of("org.springframework.web.bind.annotation.RequestMapping", "org.springframework.web.bind.annotation.RestController"));
        if (idType.equals("UUID")) {
            imports.add("java.util.UUID");
        }
        if (ops.contains(Operation.LIST)) {
            imports.addAll(List.of("java.util.List", "org.springframework.web.bind.annotation.GetMapping"));
        }
        if (ops.contains(Operation.READ)) {
            imports.add("org.springframework.web.bind.annotation.GetMapping");
        }
        if (find) {
            imports.add("org.springframework.web.bind.annotation.PathVariable");
        }
        if (find || writes) {
            imports.addAll(List.of("org.springframework.http.HttpStatus", "org.springframework.web.server.ResponseStatusException"));
        }
        if (writes) {
            imports.add("org.springframework.web.bind.annotation.RequestBody");
            typeImports(imports, body);
            if (body.stream().anyMatch(f -> f.kind() == FieldKind.ENUM)) {
                imports.add("java.util.Locale");
            }
        }
        if (ops.contains(Operation.CREATE)) {
            imports.addAll(List.of("org.springframework.web.bind.annotation.PostMapping", "org.springframework.web.bind.annotation.ResponseStatus"));
        }
        if (ops.contains(Operation.UPDATE)) {
            imports.add("org.springframework.web.bind.annotation.PutMapping");
        }
        if (ops.contains(Operation.DELETE)) {
            imports.addAll(List.of("org.springframework.web.bind.annotation.DeleteMapping", "org.springframework.web.bind.annotation.ResponseStatus"));
        }
        String request = entity + "Request";
        StringBuilder sb = new StringBuilder(header(pkg, imports));
        sb.append("/** API de ").append(entity).append(" (").append(describe(ops)).append("). Gerada pelo NOVA a partir da aba Melhorias. */\n")
                .append("@RestController\n@RequestMapping(\"").append(path).append("\")\npublic class ").append(className).append(" {\n\n")
                .append("    private final ").append(entity).append("Repository repository;\n\n")
                .append("    public ").append(className).append('(').append(entity).append("Repository repository) {\n        this.repository = repository;\n    }\n");
        if (writes) {
            sb.append("\n    /** Dados aceitos ao criar ou editar. */\n    public record ").append(request).append('(')
                    .append(String.join(", ", body.stream().map(f -> f.requestType() + " " + f.name()).toList())).append(") {}\n");
        }
        if (ops.contains(Operation.LIST)) {
            sb.append("\n    @GetMapping\n    public List<").append(entity).append("> listar() {\n        return repository.findAll();\n    }\n");
        }
        if (ops.contains(Operation.READ)) {
            sb.append("\n    @GetMapping(\"/{id}\")\n    public ").append(entity).append(" ver(@PathVariable ").append(idType)
                    .append(" id) {\n        return buscar(id);\n    }\n");
        }
        if (ops.contains(Operation.CREATE)) {
            sb.append("\n    @PostMapping\n    @ResponseStatus(HttpStatus.CREATED)\n    public ").append(entity).append(" criar(@RequestBody ").append(request)
                    .append(" dados) {\n        ").append(entity).append(" item = new ").append(entity)
                    .append("();\n        preencher(item, dados);\n        return repository.save(item);\n    }\n");
        }
        if (ops.contains(Operation.UPDATE)) {
            sb.append("\n    @PutMapping(\"/{id}\")\n    public ").append(entity).append(" editar(@PathVariable ").append(idType).append(" id, @RequestBody ")
                    .append(request).append(" dados) {\n        ").append(entity)
                    .append(" item = buscar(id);\n        preencher(item, dados);\n        return repository.save(item);\n    }\n");
        }
        if (ops.contains(Operation.DELETE)) {
            sb.append("\n    @DeleteMapping(\"/{id}\")\n    @ResponseStatus(HttpStatus.NO_CONTENT)\n    public void excluir(@PathVariable ").append(idType)
                    .append(" id) {\n        buscar(id);\n        repository.deleteById(id);\n    }\n");
        }
        if (find) {
            sb.append("\n    private ").append(entity).append(" buscar(").append(idType).append(" id) {\n        return repository.findById(id)\n")
                    .append("                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, \"").append(entity)
                    .append(" não encontrado: \" + id));\n    }\n");
        }
        if (writes) {
            sb.append("\n    private static void preencher(").append(entity).append(" item, ").append(request).append(" dados) {\n")
                    .append("        if (dados == null) {\n            throw invalido(\"Envie os dados de ").append(entity).append(".\");\n        }\n");
            for (F f : body) {
                if (!f.required()) {
                    continue;
                }
                String check = f.requestType().equals("String") ? "dados." + f.name() + "() == null || dados." + f.name() + "().isBlank()"
                        : "dados." + f.name() + "() == null";
                sb.append("        if (").append(check).append(") {\n            throw invalido(\"Informe ").append(f.name()).append(".\");\n        }\n");
            }
            for (F f : body) {
                String value = f.kind() == FieldKind.ENUM ? "opcao(" + f.enumRef() + ".class, dados." + f.name() + "(), \"" + f.name() + "\")"
                        : "dados." + f.name() + "()";
                if (f.primitive()) {
                    sb.append("        if (dados.").append(f.name()).append("() != null) {\n            item.set").append(f.pascal()).append('(').append(value)
                            .append(");\n        }\n");
                } else {
                    sb.append("        item.set").append(f.pascal()).append('(').append(value).append(");\n");
                }
            }
            sb.append("    }\n");
            if (body.stream().anyMatch(f -> f.kind() == FieldKind.ENUM)) {
                sb.append("\n    private static <E extends Enum<E>> E opcao(Class<E> tipo, String valor, String campo) {\n")
                        .append("        if (valor == null || valor.isBlank()) {\n            return null;\n        }\n        try {\n")
                        .append("            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));\n        } catch (IllegalArgumentException e) {\n")
                        .append("            throw invalido(\"Valor inválido para \" + campo + \": \" + valor);\n        }\n    }\n");
            }
            sb.append("\n    private static ResponseStatusException invalido(String mensagem) {\n")
                    .append("        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);\n    }\n");
        }
        return sb.append("}\n").toString();
    }

    static String describe(Set<Operation> ops) {
        List<String> words = new ArrayList<>();
        for (Operation op : ops) {
            words.add(switch (op) {
                case LIST -> "listar";
                case READ -> "ver";
                case CREATE -> "criar";
                case UPDATE -> "editar";
                case DELETE -> "excluir";
            });
        }
        return String.join(", ", words);
    }

    // ---------------- status ----------------

    static String statusController(String pkg, String className, String entity, F field, String method, String path, String var,
                                   List<String> allowed, String idType, Set<String> extraImports) {
        boolean isEnum = field.kind() == FieldKind.ENUM;
        Set<String> imports = new TreeSet<>(extraImports);
        imports.addAll(List.of("java.util.Locale", "java.util.Set", "org.springframework.http.HttpStatus",
                "org.springframework.web.bind.annotation.PathVariable", "org.springframework.web.bind.annotation.RequestBody",
                "org.springframework.web.bind.annotation.RestController", "org.springframework.web.server.ResponseStatusException",
                "org.springframework.web.bind.annotation." + mappingOf(method)));
        if (isEnum) {
            imports.add("java.util.EnumSet");
        }
        if (idType.equals("UUID")) {
            imports.add("java.util.UUID");
        }
        String type = isEnum ? field.enumRef() : "String";
        String set = isEnum ? "EnumSet.of(" + String.join(", ", allowed.stream().map(v -> field.enumRef() + "." + v).toList()) + ")"
                : "Set.of(" + String.join(", ", allowed.stream().map(v -> "\"" + v + "\"").toList()) + ")";
        StringBuilder sb = new StringBuilder(header(pkg, imports));
        sb.append("/** Muda ").append(field.name()).append(" de ").append(entity).append(". Gerado pelo NOVA a partir da aba Melhorias. */\n")
                .append("@RestController\npublic class ").append(className).append(" {\n\n")
                .append("    /** Valores que podem ser definidos por este endpoint. */\n")
                .append("    private static final Set<").append(type).append("> PERMITIDOS = ").append(set).append(";\n\n")
                .append("    private final ").append(entity).append("Repository repository;\n\n")
                .append("    public ").append(className).append('(').append(entity).append("Repository repository) {\n        this.repository = repository;\n    }\n\n")
                .append("    public record Alteracao(String ").append(field.name()).append(") {}\n\n")
                .append("    @").append(mappingOf(method)).append("(\"").append(path).append("\")\n")
                .append("    public ").append(entity).append(" alterar").append(field.pascal()).append("(@PathVariable(\"").append(var).append("\") ")
                .append(idType).append(" id, @RequestBody Alteracao dados) {\n")
                .append("        if (dados == null || dados.").append(field.name()).append("() == null || dados.").append(field.name()).append("().isBlank()) {\n")
                .append("            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"Informe o novo valor de ").append(field.name()).append(".\");\n        }\n")
                .append("        String valor = dados.").append(field.name()).append("().trim().toUpperCase(Locale.ROOT);\n");
        if (isEnum) {
            sb.append("        ").append(type).append(" novo;\n        try {\n            novo = ").append(type).append(".valueOf(valor);\n")
                    .append("        } catch (IllegalArgumentException e) {\n")
                    .append("            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"Valor desconhecido: \" + valor);\n        }\n");
        } else {
            sb.append("        String novo = valor;\n");
        }
        sb.append("        if (!PERMITIDOS.contains(novo)) {\n")
                .append("            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"Este valor não pode ser definido por aqui. Use: ")
                .append(String.join(", ", allowed)).append(".\");\n        }\n")
                .append("        ").append(entity).append(" item = repository.findById(id)\n")
                .append("                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, \"").append(entity)
                .append(" não encontrado: \" + id));\n")
                .append("        item.set").append(field.pascal()).append("(novo);\n        return repository.save(item);\n    }\n}\n");
        return sb.toString();
    }

    static String mappingOf(String method) {
        return switch (method) {
            case "PUT" -> "PutMapping";
            case "POST" -> "PostMapping";
            default -> "PatchMapping";
        };
    }

    // ---------------- testes ----------------

    record Id(String literal, String path, String declaration) {}

    static Id idOf(String idType) {
        return switch (idType) {
            case "Integer", "int" -> new Id("1", "1", "");
            case "String" -> new Id("\"1\"", "1", "");
            case "UUID" -> new Id("ID", "00000000-0000-0000-0000-000000000001",
                    "    private static final UUID ID = UUID.fromString(\"00000000-0000-0000-0000-000000000001\");\n\n");
            default -> new Id("1L", "1", "");
        };
    }

    private static Set<String> testImports(Set<String> extra) {
        Set<String> imports = new TreeSet<>(extra);
        imports.addAll(List.of("static org.mockito.Mockito.mock", "static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status",
                "org.junit.jupiter.api.Test", "org.springframework.test.web.servlet.MockMvc",
                "org.springframework.test.web.servlet.setup.MockMvcBuilders"));
        return imports;
    }

    static String resourceTest(String pkg, String controller, String entity, String path, Set<Operation> ops, List<F> fields, String idType,
                               Set<String> extraImports) {
        Set<String> imports = testImports(extraImports);
        Id id = idOf(idType);
        if (idType.equals("UUID")) {
            imports.add("java.util.UUID");
        }
        List<F> body = fields.stream().filter(f -> !f.statusField()).toList();
        boolean anyRequired = body.stream().anyMatch(F::required);
        StringBuilder tests = new StringBuilder();
        if (ops.contains(Operation.LIST)) {
            imports.addAll(List.of("java.util.List", "static org.mockito.Mockito.when", "static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get"));
            tests.append("    @Test\n    void listaOsItens() throws Exception {\n        when(repository.findAll()).thenReturn(List.of(new ").append(entity)
                    .append("()));\n        mvc.perform(get(\"").append(path).append("\")).andExpect(status().isOk());\n    }\n\n");
        }
        if (ops.contains(Operation.READ)) {
            imports.addAll(List.of("java.util.Optional", "static org.mockito.Mockito.when", "static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get"));
            tests.append("    @Test\n    void itemInexistenteResponde404() throws Exception {\n        when(repository.findById(").append(id.literal())
                    .append(")).thenReturn(Optional.empty());\n        mvc.perform(get(\"").append(path).append('/').append(id.path())
                    .append("\")).andExpect(status().isNotFound());\n    }\n\n");
        }
        if (ops.contains(Operation.CREATE)) {
            imports.addAll(List.of("static org.mockito.ArgumentMatchers.any", "static org.mockito.Mockito.when",
                    "static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post", "org.springframework.http.MediaType"));
            if (anyRequired) {
                tests.append("    @Test\n    void criarSemCampoObrigatorioResponde400() throws Exception {\n        mvc.perform(post(\"").append(path)
                        .append("\").contentType(MediaType.APPLICATION_JSON).content(\"{}\")).andExpect(status().isBadRequest());\n    }\n\n");
            }
            tests.append("    @Test\n    void criaComDadosValidos() throws Exception {\n        when(repository.save(any(").append(entity)
                    .append(".class))).thenAnswer(chamada -> chamada.getArgument(0));\n        mvc.perform(post(\"").append(path)
                    .append("\").contentType(MediaType.APPLICATION_JSON).content(\"").append(javaString(validJson(body)))
                    .append("\")).andExpect(status().isCreated());\n    }\n\n");
        }
        if (ops.contains(Operation.UPDATE)) {
            imports.addAll(List.of("java.util.Optional", "static org.mockito.ArgumentMatchers.any", "static org.mockito.Mockito.when",
                    "static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put", "org.springframework.http.MediaType"));
            tests.append("    @Test\n    void editaItemExistente() throws Exception {\n        when(repository.findById(").append(id.literal())
                    .append(")).thenReturn(Optional.of(new ").append(entity).append("()));\n        when(repository.save(any(").append(entity)
                    .append(".class))).thenAnswer(chamada -> chamada.getArgument(0));\n        mvc.perform(put(\"").append(path).append('/').append(id.path())
                    .append("\").contentType(MediaType.APPLICATION_JSON).content(\"").append(javaString(validJson(body)))
                    .append("\")).andExpect(status().isOk());\n    }\n\n");
        }
        if (ops.contains(Operation.DELETE)) {
            imports.addAll(List.of("java.util.Optional", "static org.mockito.Mockito.when",
                    "static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete"));
            tests.append("    @Test\n    void excluiItemExistente() throws Exception {\n        when(repository.findById(").append(id.literal())
                    .append(")).thenReturn(Optional.of(new ").append(entity).append("()));\n        mvc.perform(delete(\"").append(path).append('/')
                    .append(id.path()).append("\")).andExpect(status().isNoContent());\n    }\n\n");
        }
        return header(pkg, imports) + "/** Testes da API de " + entity + " gerados pelo NOVA. Rodam sem banco: o repositório é um dublê. */\nclass "
                + controller + "Test {\n\n" + id.declaration()
                + "    private final " + entity + "Repository repository = mock(" + entity + "Repository.class);\n"
                + "    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new " + controller + "(repository)).build();\n\n"
                + tests.toString().stripTrailing() + "\n}\n";
    }

    static String statusTest(String pkg, String controller, String entity, F field, String method, String path, String var, List<String> allowed,
                             List<String> all, String idType, Set<String> extraImports) {
        Set<String> imports = testImports(extraImports);
        imports.addAll(List.of("java.util.Optional", "static org.mockito.ArgumentMatchers.any", "static org.mockito.Mockito.when",
                "org.springframework.http.MediaType", "static org.springframework.test.web.servlet.request.MockMvcRequestBuilders."
                        + method.toLowerCase(Locale.ROOT)));
        if (idType.equals("UUID")) {
            imports.add("java.util.UUID");
        }
        Id id = idOf(idType);
        String url = path.replace("{" + var + "}", id.path());
        String call = method.toLowerCase(Locale.ROOT) + "(\"" + url + "\").contentType(MediaType.APPLICATION_JSON).content(";
        String notAllowed = all == null ? null : all.stream().filter(v -> !allowed.contains(v)).findFirst().orElse(null);
        StringBuilder sb = new StringBuilder(header(pkg, imports));
        sb.append("/** Testes da mudança de ").append(field.name()).append(" de ").append(entity).append(", gerados pelo NOVA. */\nclass ")
                .append(controller).append("Test {\n\n").append(id.declaration())
                .append("    private final ").append(entity).append("Repository repository = mock(").append(entity).append("Repository.class);\n")
                .append("    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ").append(controller).append("(repository)).build();\n\n")
                .append("    @Test\n    void alteraParaUmValorPermitido() throws Exception {\n        when(repository.findById(").append(id.literal())
                .append(")).thenReturn(Optional.of(new ").append(entity).append("()));\n        when(repository.save(any(").append(entity)
                .append(".class))).thenAnswer(chamada -> chamada.getArgument(0));\n        mvc.perform(").append(call).append("\"")
                .append(javaString("{\"" + field.name() + "\":\"" + allowed.getFirst().toLowerCase(Locale.ROOT) + "\"}"))
                .append("\")).andExpect(status().isOk());\n    }\n\n")
                .append("    @Test\n    void recusaValorDesconhecido() throws Exception {\n        mvc.perform(").append(call).append("\"")
                .append(javaString("{\"" + field.name() + "\":\"VALOR_QUE_NAO_EXISTE\"}")).append("\")).andExpect(status().isBadRequest());\n    }\n\n");
        if (notAllowed != null) {
            sb.append("    @Test\n    void recusaValorNaoPermitido() throws Exception {\n        mvc.perform(").append(call).append("\"")
                    .append(javaString("{\"" + field.name() + "\":\"" + notAllowed + "\"}")).append("\")).andExpect(status().isBadRequest());\n    }\n\n");
        }
        sb.append("    @Test\n    void itemInexistenteResponde404() throws Exception {\n        when(repository.findById(").append(id.literal())
                .append(")).thenReturn(Optional.empty());\n        mvc.perform(").append(call).append("\"")
                .append(javaString("{\"" + field.name() + "\":\"" + allowed.getFirst() + "\"}")).append("\")).andExpect(status().isNotFound());\n    }\n}\n");
        return sb.toString();
    }

    /** Teste de fumaça dos GETs sem parâmetros de um controller existente, com as dependências do construtor como dublês. */
    static String getsTest(String pkg, String controller, List<Facts.CtorParam> ctor, List<String> paths, Set<String> extraImports) {
        Set<String> imports = testImports(extraImports);
        imports.add("static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get");
        StringBuilder sb = new StringBuilder(header(pkg, imports));
        sb.append("/** Testes de fumaça de ").append(controller).append(" gerados pelo NOVA: cada GET responde sem erro. */\nclass ")
                .append(controller).append("Test {\n\n");
        List<String> args = new ArrayList<>();
        for (Facts.CtorParam p : ctor) {
            sb.append("    private final ").append(p.type()).append(' ').append(p.name()).append(" = mock(").append(p.type()).append(".class);\n");
            args.add(p.name());
        }
        sb.append("    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ").append(controller).append('(')
                .append(String.join(", ", args)).append(")).build();\n");
        for (String path : paths) {
            sb.append("\n    @Test\n    void get").append(Names.pascal(path.replace("/api/", "").isBlank() ? "raiz" : path.replace("/api/", "")))
                    .append("Responde() throws Exception {\n        mvc.perform(get(\"").append(path).append("\")).andExpect(status().is2xxSuccessful());\n    }\n");
        }
        return sb.append("}\n").toString();
    }

    static String sample(F f, int i) {
        return switch (f.kind()) {
            case INTEGER, LONG -> String.valueOf(i);
            case DECIMAL, DOUBLE -> String.valueOf(10 * i) + ".5";
            case BOOLEAN -> "true";
            case DATE -> "\"2026-01-1" + i + "\"";
            case DATETIME -> "\"2026-01-1" + i + "T10:00:00\"";
            case INSTANT -> "\"2026-01-1" + i + "T10:00:00Z\"";
            case UUID -> "\"00000000-0000-0000-0000-00000000000" + i + "\"";
            case ENUM -> "\"" + (f.values() == null || f.values().isEmpty() ? "A" : f.values().getFirst()) + "\"";
            default -> "\"" + Names.pascal(f.name()) + " " + i + "\"";
        };
    }

    static String validJson(List<F> fields) {
        List<String> parts = new ArrayList<>();
        for (F f : fields) {
            parts.add("\"" + f.name() + "\":" + sample(f, 1));
        }
        return "{" + String.join(",", parts) + "}";
    }

    static String javaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ---------------- banco ----------------

    static String migration(String database, String table, List<F> fields) {
        boolean postgres = "postgresql".equals(database);
        StringBuilder sb = new StringBuilder("-- Tabela criada pelo NOVA a partir da aba Melhorias.\nCREATE TABLE ").append(table).append(" (\n");
        sb.append(postgres ? "    id BIGINT GENERATED BY DEFAULT AS IDENTITY,\n" : "    id BIGINT NOT NULL AUTO_INCREMENT,\n");
        for (F f : fields) {
            String type = switch (f.kind()) {
                case LONG_TEXT -> "VARCHAR(4000)";
                case INTEGER -> "INT";
                case LONG -> "BIGINT";
                case DECIMAL -> "DECIMAL(12,2)";
                case DOUBLE -> postgres ? "DOUBLE PRECISION" : "DOUBLE";
                case BOOLEAN -> "BOOLEAN";
                case DATE -> "DATE";
                case DATETIME -> postgres ? "TIMESTAMP(6)" : "DATETIME(6)";
                case INSTANT -> postgres ? "TIMESTAMP(6) WITH TIME ZONE" : "DATETIME(6)";
                case ENUM -> "VARCHAR(30)";
                default -> "VARCHAR(255)";
            };
            sb.append("    ").append(f.column()).append(' ').append(type).append(f.required() ? " NOT NULL" : " NULL").append(",\n");
        }
        sb.append("    CONSTRAINT pk_").append(table).append(" PRIMARY KEY (id)\n);\n");
        return sb.toString();
    }

    static int nextMigration(Sources src, String dir) {
        int max = 0;
        Matcher m = Pattern.compile("V(\\d+)__").matcher("");
        for (String p : src.paths(Pattern.quote(dir) + "V\\d+__.+\\.sql")) {
            m.reset(Sources.fileName(p));
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return max + 1;
    }

    // ---------------- segurança ----------------

    /**
     * Libera rotas no SecurityFilterChain gerado, inserindo regras antes de {@code .anyRequest()}.
     *
     * @return o arquivo alterado, ou {@code null} quando o formato não permite editar com segurança
     */
    static String permit(String text, List<String[]> rules) {
        int at = text.indexOf(".anyRequest()");
        if (at < 0) {
            return null;
        }
        int lineStart = text.lastIndexOf('\n', at) + 1;
        String before = text.substring(lineStart, at);
        if (!before.isBlank()) {
            return null;
        }
        StringBuilder insert = new StringBuilder();
        if (!text.contains("\"/error\"")) {
            // Sem isto, um 404 ou 400 vira 401: o Spring encaminha o erro para /error, que fica protegido.
            insert.append(before).append(".requestMatchers(\"/error\").permitAll()\n");
        }
        for (String[] rule : rules) {
            String line = ".requestMatchers(HttpMethod." + rule[0] + ", \"" + rule[1] + "\").permitAll()";
            if (!text.contains(line)) {
                insert.append(before).append(line).append('\n');
            }
        }
        String result = text.substring(0, lineStart) + insert + text.substring(lineStart);
        if (!result.contains("import org.springframework.http.HttpMethod;")) {
            Matcher m = Pattern.compile("(?m)^import [^;]+;\\s*$").matcher(result);
            int last = -1;
            while (m.find()) {
                last = m.end();
            }
            if (last < 0) {
                return null;
            }
            result = result.substring(0, last) + "\nimport org.springframework.http.HttpMethod;" + result.substring(last);
        }
        return result;
    }

    // ---------------- página Next.js ----------------

    static String nextListPage(String route, String title, String apiPath, String entity, List<F> columns) {
        String component = Names.pascal(route.isBlank() || route.equals("/") ? "inicio" : route) + "Page";
        StringBuilder type = new StringBuilder("type ").append(entity).append(" = {\n  id: number | string;\n");
        for (F f : columns) {
            type.append("  ").append(f.name()).append("?: ").append(tsType(f)).append(" | null;\n");
        }
        type.append("};\n");
        StringBuilder head = new StringBuilder();
        StringBuilder cells = new StringBuilder();
        for (F f : columns) {
            head.append("              <th style={{ textAlign: \"left\", padding: \"8px 12px\" }}>").append(jsx(label(f.name()))).append("</th>\n");
            cells.append("                <td style={{ padding: \"8px 12px\" }}>").append(cell(f)).append("</td>\n");
        }
        return "import { api } from \"@/lib/api\";\n\n" + type + "\n"
                + "/** Lista de " + entity + " vinda da API (" + apiPath + "). Gerada pelo NOVA a partir da aba Melhorias. */\n"
                + "export const dynamic = \"force-dynamic\";\n\n"
                + "export default async function " + component + "() {\n"
                + "  let itens: " + entity + "[] = [];\n  let erro: string | null = null;\n"
                + "  try {\n    itens = await api<" + entity + "[]>(\"" + apiPath + "\");\n  } catch {\n"
                + "    erro = \"Não foi possível carregar os dados agora. Tente de novo em instantes.\";\n  }\n\n"
                + "  return (\n    <main style={{ padding: 32 }}>\n      <h1>" + jsx(title) + "</h1>\n"
                + "      {erro ? (\n        <p role=\"alert\">{erro}</p>\n      ) : itens.length === 0 ? (\n"
                + "        <p>Nenhum item cadastrado ainda.</p>\n      ) : (\n"
                + "        <table style={{ borderCollapse: \"collapse\", width: \"100%\" }}>\n          <thead>\n            <tr>\n" + head
                + "            </tr>\n          </thead>\n          <tbody>\n            {itens.map((item) => (\n              <tr key={item.id}>\n" + cells
                + "              </tr>\n            ))}\n          </tbody>\n        </table>\n      )}\n    </main>\n  );\n}\n";
    }

    private static String tsType(F f) {
        return switch (f.kind()) {
            case INTEGER, LONG, DECIMAL, DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    private static String cell(F f) {
        String v = "item." + f.name();
        if (f.kind() == FieldKind.BOOLEAN) {
            return "{" + v + " ? \"Sim\" : \"Não\"}";
        }
        if (f.kind() == FieldKind.DECIMAL && Names.normalize(f.name()).matches(".*(preco|valor|total|price|amount|custo|salario).*")) {
            return "{" + v + " == null ? \"\" : Number(" + v + ").toLocaleString(\"pt-BR\", { style: \"currency\", currency: \"BRL\" })}";
        }
        return "{" + v + " ?? \"\"}";
    }

    static String label(String name) {
        String spaced = String.join(" ", Names.words(name)).toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? name : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    static String jsx(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("{", "&#123;").replace("}", "&#125;");
    }
}
