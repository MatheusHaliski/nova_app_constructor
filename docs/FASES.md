# Fases de implementação do NOVA

O backlog completo está no Trello (board **Nova Application Constructor**). Cada fase entrega um conjunto de RFs
e histórias (HU) de lá. A barra lateral do app já mostra todos os grupos de RF; os que ainda não existem abrem a
página "Em construção" com o RF e a fase prevista.

| Fase | Entrega | RFs / HUs principais | Situação |
|---|---|---|---|
| 1 | Fundação: autenticação, cadastro, controle de acesso, configurações do perfil (LGPD), marca e catálogo de tecnologias | Cartões [Infra], [Backend], [Frontend]; RF-3.05 (LGPD); RF-4.04/4.25 (acervo e fichas) | **Concluída** |
| 2 | Meus projetos: salvar, página do projeto, árvore de arquivos, editor, versões, download | RF-2.01 a RF-2.06, RF-2.08, RF-2.10 | **Entregue (base)** |
| 3 | Criar app: formulário (Copilot total ou assistido), stack com ícones, telas, autenticação opcional, IA e acervo RAG | Cartões [Obj 1] | **Entregue (estrutura)** |
| 4 | Rede NOVA: visibilidade, compartilhamento, feed, explorar, fork, comentários, notificações, moderação | RF-3.01 a RF-3.04 | **Entregue (base)** |
| 5 | Abstração visual do backend e do frontend | RF-4.01 a RF-4.35 (MVP) | Planejada |
| 6 | Integrações (git, IDE, CLI/API), execução em sandbox, deploy e conectores opcionais | RF-2.07, RF-2.09 | Planejada |

## Fase 1 — o que foi entregue

**Backend** (Java 21, Spring Boot 4.1, Spring Security 7, Spring Data JPA, Flyway, MySQL)
- Cadastro com dados mínimos (nome, usuário, e-mail, senha, data de nascimento só para idade mínima de 18 anos) e aceite de termos por ação afirmativa, com versão e data registradas.
- Login com bloqueio após 5 tentativas, access token JWT RS256 próprio (15 min) e refresh token opaco com rotação; reuso de token revoga a sessão inteira.
- Controle de acesso por perfil (USER, ADMIN) na rota e no método; respostas 401/403 em ProblemDetail.
- Configurações do perfil no padrão LGPD: correção de dados (art. 18, III), visibilidade com padrão "Somente você" (art. 18, VII), um consentimento por finalidade, todos desligados e revogáveis (art. 7º, I; art. 8º, §5º; art. 18, IX), histórico imutável de consentimentos (art. 8º, §2º), exportação JSON (art. 18, V) e exclusão de conta com senha e confirmação (art. 18, VI).
- Administração: lista de usuários, promoção a ADMIN, desativação, proteção contra remover o último admin; painel de fontes de dados (MySQL central e conectores opcionais desligados).
- 14 testes de integração.

**Frontend** (Next.js 16, React 19, TypeScript, Tailwind CSS)
- Telas de login e cadastro; sessão em cookies httpOnly (o navegador nunca vê os tokens); `proxy.ts` protege rotas e renova o token.
- Barra superior com logo NOVA, notificações e menu do perfil; barra lateral com ícones de todos os grupos de RF do Trello, recolhível e em gaveta no celular.
- Marca redesenhada em SVG a partir do logo do Trello; interface em Montserrat (fonte identificada no logo) e ícones próprios no mesmo estilo geométrico, com o degradê da marca no item ativo.
- Configurações: Conta, Privacidade, Aparência (tema claro/escuro/sistema), Idioma (pt-BR, en-US, es-ES) e Seus dados, cada controle com a etiqueta do artigo da LGPD.
- Catálogo de tecnologias com ícones e busca (base para formulários de stack e para o feed).

## Fases 2 e 3 — o que foi entregue

**Catálogo de tecnologias** (`backend/src/main/resources/catalog/catalog.json`, `GET /api/v1/catalog`)
- Fonte única do formulário, do Copilot e do gerador: linguagens e versões, frameworks e versões, ferramentas de build com os comandos de compilar/rodar/testar, pacotes e plugins de cada framework, bancos e versões, conectores opcionais e provedores de IA.
- Geradores disponíveis: Spring Boot (Maven ou Gradle), NestJS, FastAPI, Next.js e React + Vite. As demais stacks aparecem no formulário como "em breve".
- As versões do catálogo foram conferidas no Maven Central, npm e PyPI em 19/11/2026.

**Gerador** (`POST /api/v1/generator/plan` e `/generator/projects`)
- Valida as escolhas contra o catálogo (existência, disponibilidade, compatibilidade linguagem × framework × build), preenche os padrões e resolve dependências, plugins e variáveis de ambiente, com o motivo de cada pacote.
- Gera a estrutura inicial: build, aplicação, configuração por variáveis (sem segredos no código), migração inicial, autenticação JWT, conectores, IA externa, testes, Dockerfile, docker-compose, CI, README com "como compilar" e o manifesto `nova.json`.
- Verificado com build real dos projetos gerados: Spring Boot/Maven (todos os conectores, autenticação e IA), Spring Boot/Gradle, NestJS, Next.js e React + Vite. O FastAPI teve só a sintaxe conferida.

**Copilot** (`/api/v1/copilot`)
- Copilot total: da ideia em texto para a proposta completa (nome, objetivo, tecnologias, recursos, telas, requisitos e backlog), com saída estruturada do Claude (`claude-opus-5-5`) validada pelo catálogo.
- Assistido: sugestão por campo (descrição, objetivo, telas, requisitos).
- Sem `ANTHROPIC_API_KEY`, ou se a IA falhar ou recusar, o Copilot usa regras simples por palavras-chave e avisa o usuário.

**Projetos** (`/api/v1/projects`)
- Projeto nasce privado; projetos de outras pessoas respondem 404.
- Cada alteração cria uma versão imutável; salvar exige a versão base (conflito 409 se alguém alterou antes); restaurar cria nova versão.
- Criar, editar, mover e excluir arquivos e pastas com validação de caminho; aviso de credenciais ao salvar.
- Download em ZIP ou tar.gz do projeto, de uma pasta ou de uma versão, sem arquivos `.env`, com checksum SHA-256.
- Arquivar, lixeira com restauração e exclusão definitiva com confirmação pelo nome; limites de tamanho e de arquivos.

**Frontend**
- `/novo-app`: formulário em etapas (Modo, Projeto, Tecnologias, Pacotes e conectores, Telas, Revisão) com os ícones das tecnologias.
- `/projetos` e `/projetos/[id]`: lista com selos de stack, ficha técnica, árvore de arquivos com editor, versões e configurações.

**Ainda não feito nestas fases:** editor com realce de sintaxe, comparação (diff) entre versões, upload de arquivos, salvamento automático, backlog e diagramas gerados, editor visual de telas (ScreenSpec), acervo RAG, textos do formulário e dos projetos em inglês e espanhol.

## Fase 4 — o que foi entregue

**Compartilhamento (aba "Compartilhar" do projeto)**
- Quatro níveis: Privado (padrão), Link, Comunidade e Público.
- Ampliar a visibilidade passa por verificações: senha atual, licença escolhida para entrar na rede
  (MIT, Apache 2.0, GPL 3.0 ou todos os direitos reservados), nome do projeto digitado para tornar público e
  varredura de senhas e chaves nos arquivos que ficarão visíveis. Reduzir é imediato.
- Arquivos e pastas ocultos: só o dono vê; não entram em downloads nem em forks. Arquivos `.env` nunca são compartilhados.
- Links de compartilhamento sem login (`/s/<token>`), com validade e revogação. O endereço aparece uma única vez;
  o servidor guarda só o hash. Tornar o projeto privado revoga todos os links.

**Rede**
- Feed, Explorar (busca por texto e filtro por tecnologia, com os ícones do catálogo), Estrelas e Compartilhados.
- Página do projeto na rede, somente leitura: arquivos visíveis, download, estrela, fork, comentários e denúncia.
- Fork cria um projeto privado para quem copiou, com a origem registrada ("Fork de @autor/projeto").
  Projetos com todos os direitos reservados não permitem fork.
- Notificações no sino e em `/notificacoes` para estrela, fork, comentário e decisões da moderação.
- Moderação (administradores): fila de denúncias com descartar, ocultar comentário ou despublicar o projeto;
  a pessoa afetada é avisada com o motivo.

**API**: `PUT /api/v1/projects/{id}/sharing`, `/sharing/links`, `/api/v1/community/**`, `/api/v1/shared/{token}/**`
(sem login), `/api/v1/me/notifications`, `/api/v1/admin/reports`.

**Ainda não entregue nesta fase**: perfil público e portfólio, seguir pessoas, convites com papéis por pessoa,
bloqueio entre usuários, recurso contra decisões de moderação, paginação infinita do feed e inclusão dos dados da rede
na exportação da conta (LGPD).

## Visualizador do projeto, dados do domínio e propostas de experiência — o que foi entregue

**Criação**
- Etapa "Dados": entidades com campos e tipos (texto, decimal, data, status com valores…) e requisitos com critério de aceite e prioridade.
- Telas com modelo (lista, detalhe, formulário, painel, login…), entidade, acesso e menu; tema por tokens (cor, fonte, cantos, espaçamento).
- O gerador escreve entidades, repositórios, API CRUD, rota de status, migrações (MySQL e PostgreSQL) e testes no Spring Boot,
  e páginas ligadas à API com menu e tema no Next.js. O Copilot propõe entidades, telas e requisitos.
- Comparar stacks antes de gerar, começar de um esboço (imagem, com IA) e remixar um projeto da rede.

**Visualizador do projeto** (abas Painel, Melhorias e Prévia)
- Painel: saúde, caminho até publicar e janelas de camadas, API, dados, telas, fluxos, requisitos, privacidade (LGPD),
  acessibilidade, dependências e "perguntar ao projeto" (IA).
- Melhorias: propostas editáveis, código que muda, prévia das telas com a mudança, aplicar como nova versão, dispensar,
  aplicar com IA e sugestões de outras pessoas da rede.
- Prévia: o código do frontend da versão roda num iframe isolado com dados de exemplo; larguras, estados (vazio, carregando,
  erro, API lenta), visitante ou pessoa logada, e edição direta (clicar abre o arquivo na linha; dois cliques alteram o texto).
- Máquina do tempo nas versões: diferença de arquivos, rotas, entidades e telas.

**Rede e conta**: perfil público com portfólio e projetos fixados, painel inicial que orienta, sessões com dispositivo,
último acesso e links do portfólio nas configurações.

**Campos que faltavam nas entidades (V5)**: último acesso e troca de senha, site e GitHub (users); dispositivo e último uso
(refresh_tokens); versão da política e canal (consent_events); última abertura (projects); nome do link (share_links).

**Verificado**: 44 testes do backend; NOVA em MySQL 8.0 com as migrações V1–V5; projetos gerados compilam (Maven e
next build), 17 testes gerados passam e a API roda em MySQL 8.0 e PostgreSQL 16; prova no navegador com 26 verificações
(formulário assistido, Copilot, painel, melhorias, prévia com edição, máquina do tempo, Meus projetos, perfil e sessões).

**Ainda não entregue**: histórias que viram teste, revisar pelo celular (tela dedicada), prévia para React com Vite,
compilação real em sandbox (Fase 6) e o Copilot com IA de verdade (testado só pelas regras, sem chave configurada).

