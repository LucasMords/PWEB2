# AGENTS.md

Instruções para agentes de código neste repositório. O funcionamento do sistema está no
`README.md` (regras de negócio, camadas, operação) e no `SECURITY.md` (acessos, contas,
proteções): leia a parte relacionada antes de mudar uma regra e não a duplique aqui.

## Projeto

Sistema de consultas de uma clínica, da disciplina PWEB II (IFTO): Java 25, Spring Boot 4,
Spring MVC + Thymeleaf, Spring Data JPA, Spring Security, PostgreSQL + Flyway, Bootstrap 5.
Pacote raiz: `br.edu.ifto.aula1`. Branch principal: `master`.

## Comandos

Exigem `JAVA_HOME` apontando para um JDK 25. No PowerShell, troque `./mvnw` por `.\mvnw.cmd`; no Git Bash, use `./mvnw`.

| Para | Comando |
|---|---|
| Rodar todos os testes (não precisa de banco) | `./mvnw -q test` |
| Rodar uma classe de teste | `./mvnw -q test -Dtest=AgendaServiceTest` |
| Verificação do projeto (a mesma do CI) | `./mvnw -B verify` |
| Subir a aplicação (precisa do PostgreSQL, `DB_SENHA`) | `./mvnw spring-boot:run` |
| Subir em outra porta (a 8080 pode estar com a IDE) | `./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8099"` |
| Aplicação e banco no Docker | `docker compose up --build` (com o `.env`) |

`spring-boot:run` aplica as migrações pendentes no banco local, que é persistente.

## Código

- Nomes de domínio, comentários e mensagens ao usuário em português, com acentos nos textos.
  Pacotes em minúsculas. O `README.md` é escrito sem acentos e o `SECURITY.md`, em inglês.
- Comentários explicam o porquê, não o que o código já diz.
- **Camadas:** o controller valida o formulário, chama o serviço e redireciona. Regras e
  transações ficam no serviço; só ele usa os repositórios. Exceções atuais: as classes de
  `security` e o `UsuarioController` (não imite este; refatore só se a issue pedir).
- **Serviços:** injeção pelo construtor; `@Transactional(readOnly = true)` na classe e
  `@Transactional` nos métodos de escrita. Na criação, use `save()`; na edição, altere a
  entidade gerenciada, sem `save()`.
- **Erros:** `RegraNegocioException` para regra violada; `RegistroNaoEncontradoException` para 404.
- **JPA:** repositórios são interfaces `JpaRepository`, sem `@Repository`. Relações `LAZY` e
  `open-in-view` desligado: use `@EntityGraph` ou `join fetch` e evite N+1.
- **Hora:** regras de horário usam o `Clock` injetado, nunca `LocalDateTime.now()` direto.
- **Concorrência:** reserva e mudança de status leem o registro que decide a regra com
  bloqueio pessimista; a restrição única do banco é a segunda barreira.
- **Validação:** sempre no servidor. Formulários com `novalidate`, `th:errorclass` e
  `th:errors`; sucesso por `addFlashAttribute("mensagem", ...)`.
- **Telas:** reutilize `templates/fragments/layout.html`. A CSP bloqueia scripts e estilos
  inline: JavaScript vai em `static/js` e CSS, em `static/css`.

## Banco

- O esquema só muda por migração nova em `src/main/resources/db/migration`; nunca edite uma
  já aplicada em algum banco ou commitada. O Hibernate apenas valida (`ddl-auto=validate`):
  entidade e migração mudam juntas.
- O SQL precisa funcionar no PostgreSQL 18 e no H2 (modo PostgreSQL) dos testes.
- `db/demo` (desenvolvimento e testes) e `db/migration` compartilham a numeração do Flyway:
  uma versão nova deve ser maior que todas as existentes nas duas pastas.
- Novo valor em `AcaoAuditoria` exige recriar `ck_auditoria_acao` em uma migração.

## Segurança e dados pessoais

- Rota nova precisa de regra em `SecurityConfiguration` e teste de quem pode e quem não pode
  acessá-la. Não afrouxe CSRF, CSP nem os perfis para fazer algo funcionar.
- Médico e paciente vêm da conta autenticada, nunca de um id do formulário ou da URL, quando
  a operação é sobre os próprios dados.
- Operações relevantes são auditadas por `AuditoriaService`, na mesma transação. Login
  e bloqueio são a exceção: usam `registrarSeparado`, em transação própria, de propósito.
- Senhas, hashes, observações de consulta e dados de pacientes nunca vão para logs ou auditoria.
  As observações só são lidas pelo paciente e pelo médico que atendeu.
- Não commite `.env`, `backups/` nem senhas. Use apenas dados fictícios em testes e demonstração.

## Testes

- Regra de serviço: teste unitário com Mockito e relógio fixo, em `src/test/.../service`.
- Rota, permissão ou tela: `@SpringBootTest` com MockMvc (`*IntegrationTests`).
- Nomes de teste em português, descrevendo o comportamento (`trocaASenhaEEncerraAsOutrasSessoes`).
- Os testes locais usam H2; o CI repete contra o PostgreSQL 18.

## Fluxo de trabalho com subagentes

1. Divida o trabalho aprovado em issues individuais no GitHub. Cada issue implementa uma única atualização e cita os identificadores do plano que atende (requisitos, regras de negócio, tarefas). Uma issue = uma worktree = uma branch = um PR. A aprovação do plano vale para as issues dele: os subagentes não pedem nova aprovação nem criam outros subagentes. Só o agente principal cria subagentes.
2. Para cada issue, rode um subagente de implementação, que:
   a. pega a issue e cria uma worktree própria, com branch a partir da branch principal atualizada;
   b. implementa só essa issue, com testes, e roda os comandos de verificação do projeto (lint, testes, build) até tudo passar; PR que só muda documentação dispensa os testes locais, mas não o CI verde;
   c. envia o PR com "Closes #<número>" e: o que foi feito, identificadores atendidos, como testar, pendências;
   d. corrige qualquer falha do CI, na mesma branch;
   e. se a branch principal avançar e houver conflito, atualiza a branch (git fetch origin && git rebase origin/<branch-principal>), roda os comandos de verificação de novo e envia com git push --force-with-lease.
3. Com o PR aberto, rode um subagente revisor, diferente do que implementou. Ele lê a issue e o diff (gh pr diff), confere se o PR atende só ao escopo da issue, se os testes cobrem a mudança e se as regras deste documento foram seguidas, e registra a revisão no PR com gh pr review --comment. O subagente da issue corrige o que ele apontar, na mesma branch, e o revisor confere de novo até não restar pendência.
4. Sugestão do revisor que contrarie requisito do plano ou regra deste documento não é aplicada: responda no PR com o motivo. Se depender de regra de negócio, PARE e pergunte.
5. Com a revisão atendida e o CI verde, faça o squash merge (gh pr merge --squash --delete-branch) e remova a worktree (git worktree remove). O título do squash segue Conventional Commits.
6. Issue que depende de outra só começa depois do squash dela.
7. Com o sistema em produção, o fluxo é o mesmo, mas o squash merge é do responsável: o subagente avisa que o PR está pronto e só remove a worktree depois de confirmar o merge (gh pr view --json state retorna MERGED).

## Antes de concluir

1. Rode a verificação do projeto até tudo passar (PR só de documentação dispensa os testes
   locais, mas não o CI verde).
2. Atualize `README.md` ou `SECURITY.md` se o comportamento mudou.
3. Variável de ambiente nova: inclua em `.env.exemplo` e `docker-compose.yml`.
4. Só commite trabalho de um plano aprovado, pelo fluxo acima. Commits seguem Conventional
   Commits, em português. Autor com o e-mail noreply do GitHub (o push com e-mail pessoal é
   recusado). Issues, PRs e comentários não contêm senhas nem dados pessoais.
5. Peça confirmação antes de apagar dados, recriar o banco, restaurar backup ou reescrever o
   histórico do git, exceto o rebase da branch da própria issue previsto no fluxo.
