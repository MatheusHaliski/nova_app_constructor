# Fases de implementação do NOVA

O backlog completo está no Trello (board **Nova Application Constructor**). Cada fase entrega um conjunto de RFs
e histórias (HU) de lá. A barra lateral do app já mostra todos os grupos de RF; os que ainda não existem abrem a
página "Em construção" com o RF e a fase prevista.

| Fase | Entrega | RFs / HUs principais | Situação |
|---|---|---|---|
| 1 | Fundação: autenticação, cadastro, controle de acesso, configurações do perfil (LGPD), marca e catálogo de tecnologias | Cartões [Infra], [Backend], [Frontend]; RF-3.05 (LGPD); RF-4.04/4.25 (acervo e fichas) | **Concluída** |
| 2 | Meus projetos: salvar, página do projeto, árvore de arquivos, editor, versões, download | RF-2.01 a RF-2.06, RF-2.08, RF-2.10 | Próxima |
| 3 | Criar app: formulário (Copilot total ou assistido), stack com ícones, telas, autenticação opcional, IA e acervo RAG | Cartões [Obj 1] | Planejada |
| 4 | Rede NOVA: visibilidade, compartilhamento, feed, explorar, fork, comentários, notificações, moderação | RF-3.01 a RF-3.04 | Planejada |
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
