# Sistema de Consultas - PWEB II

Aplicacao Spring Boot para gerenciamento de pacientes, medicos e consultas. Demonstra associacoes JPA bidirecionais, repositorios Spring Data JPA, camada de servico transacional, Bean Validation e CRUD com Spring MVC e Thymeleaf.

## Tecnologias e modelo

- Java 25, Spring Boot, Spring Web MVC e Thymeleaf
- Spring Data JPA / Hibernate e PostgreSQL, com o esquema versionado pelo Flyway
- Bean Validation (Jakarta Validation / Hibernate Validator)
- Bootstrap 5.3 (via CDN) com fragmentos Thymeleaf para o layout
- `Medico 1:N Agenda`, `Agenda 1:N Agendamento`, `Paciente 1:N Agendamento` e `Agendamento 1:0..1 Consulta`
- A consulta so existe depois que a clinica confirma a presenca do paciente; paciente e medico dela vem do agendamento (veja "Agenda, agendamentos e consultas")

## Banco de dados

O banco e o PostgreSQL e os dados persistem entre execucoes. As tabelas sao criadas e
evoluidas pelo Flyway, nunca pelo Hibernate (que so confere, ao subir, se as entidades
batem com as tabelas: `ddl-auto=validate`):

- `src/main/resources/db/migration`: o esquema (`V1__esquema_inicial.sql`) e os perfis.
  Toda mudanca de tabela entra num arquivo novo (`V2__...`); um arquivo ja aplicado nunca
  e editado.
- `src/main/resources/db/demo`: pacientes, medicos e agenda de exemplo. So entram em
  desenvolvimento e nos testes; o perfil `prod` nao carrega esta pasta.

A conexao vem das variaveis de ambiente `DB_URL` (padrao
`jdbc:postgresql://localhost:5432/clinica`), `DB_USUARIO` (padrao `clinica`) e `DB_SENHA`.

**Instalacao local (Windows).** O PostgreSQL 18 foi instalado como servico do Windows
(`postgresql-x64-18`, porta 5432), com o banco `clinica` e o usuario `clinica`, dono do
banco. As tres variaveis acima estao definidas no ambiente do usuario do Windows.

**Backup.** `scripts\backup-banco.ps1` (instalacao local) e `scripts/backup-docker.sh`
(Docker Compose) gravam um `pg_dump` compactado em `backups/` e mantem os 14 mais
recentes; os comandos de restauracao estao no inicio de cada script. Para backup diario,
agende o script (Agendador de Tarefas do Windows ou cron).

Para comecar do zero em desenvolvimento, apague e recrie o banco `clinica`; o Flyway
recria as tabelas e os dados de exemplo na proxima execucao.

## Execucao

```cmd
mvnw.cmd spring-boot:run
```

Abra `http://localhost:8080`. As contas de exemplo estao no `SECURITY.md`.

Testes: `mvnw.cmd test`. Eles rodam num H2 em memoria no modo PostgreSQL, com as mesmas
migracoes (`src/test/resources/config/application.properties`), e nao precisam do banco
instalado. No CI rodam contra um PostgreSQL de verdade; para fazer o mesmo localmente,
defina `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`
apontando para um banco vazio.

## Operacao

**Docker.** O `Dockerfile` compila em uma etapa com o JDK 25 e roda em outra so com o JRE,
como usuario sem privilegios, com as camadas do Spring Boot separadas (bibliotecas em
cache, so as classes mudam a cada build):

```cmd
copy .env.exemplo .env
docker compose up --build
```

O Compose sobe a aplicacao e um PostgreSQL 18, com os dados num volume. As senhas ficam
no `.env` (fora do git): `DB_SENHA`, `CLINICA_SENHA_ADMIN` (obrigatoria: a conta `admin`
e criada com ela na primeira execucao) e, opcional, `CLINICA_SENHA_ATENDENTE`.

A imagem ativa o perfil `prod` (`application-prod.properties`): so as migracoes do
esquema, sem dados de exemplo, sem SQL no log, cache de templates ligado, logs em JSON (formato ECS, uma linha por evento) e
cookie de sessao `Secure`. Os logs tambem vao para `logs/clinica.log` (JSON, com rotacao
a cada 10 MB ou por dia, guardados por 14 dias e ate 500 MB no total; o caminho muda com a
variavel `LOG_ARQUIVO`); no Compose, a pasta e um volume. O HTTPS fica num proxy reverso na frente da aplicacao; os
cabecalhos `X-Forwarded-*` sao respeitados.

**Health check.** `GET /actuator/health` (publico) responde `{"status":"UP"}`;
`/actuator/health/liveness` e `/actuator/health/readiness` servem para orquestradores.
So o ADMIN ve os detalhes (banco, disco). Nenhum outro endpoint do Actuator e exposto. O
`HEALTHCHECK` do Dockerfile usa o liveness.

**CI.** `.github/workflows/ci.yml` roda a cada push na `master` e em pull requests: compila
e executa todos os testes contra um PostgreSQL 18 (`mvnw verify`, com os relatorios
anexados se falhar), depois sobe aplicacao e banco com o Docker Compose, espera o health
check e confere o login do admin. Nada e publicado.

**Erros.** Erros inesperados mostram uma pagina generica (`templates/error/5xx.html`);
o detalhe tecnico fica so no log.

## Painel inicial e busca de pacientes

A pagina inicial mostra um painel conforme o perfil, so com dados que o perfil ja pode ver:

- administracao e atendimento: o resumo de hoje de todos os medicos (horarios, livres,
  aguardando, realizadas e faltas), com atalho para a agenda do dia;
- medico: os proprios horarios de hoje, com o paciente e a situacao de cada um;
- paciente: o proximo agendamento (data, hora, medico e especialidade).

A lista de pacientes e paginada (10 por pagina, em ordem alfabetica) e tem busca por parte
do nome, sem diferenciar maiusculas, ou pelos digitos do CPF, com ou sem pontuacao. O
texto e procurado literalmente: `%` e `_` nao funcionam como curingas.

**Fuso horario.** A aplicacao usa o fuso do Tocantins (`America/Araguaina`) em qualquer
maquina ou container; para trocar, defina a variavel de ambiente `CLINICA_FUSO`.

## Camadas

| Pacote | Papel |
|---|---|
| `controller` | Recebe a requisicao, valida o formulario (`@Valid`, `BindingResult`), chama o servico e redireciona |
| `service` | Regras e transacoes; unico ponto que acessa os repositorios |
| `model.repository` | Interfaces Spring Data JPA |
| `model.entity` | Entidades JPA com as restricoes de validacao |
| `dto` | Objetos dos formularios e dos filtros da agenda (`AgendaForm`, `AgendamentoForm`, `AgendaFiltro`...) |
| `validation` | Credenciais do cadastro (`Credenciais`, `CredenciaisValidator`) |

## Agenda, agendamentos e consultas

| Entidade | O que e | Status |
|---|---|---|
| `Agenda` | Horario disponibilizado por um medico (medico, data, horario) | `DISPONIVEL`, `AGENDADO` |
| `Agendamento` | Reserva de um horario por um paciente | `AGENDADO`, `REALIZADO`, `FALTOU`, `CANCELADO` |
| `Consulta` | Atendimento realizado, criado na confirmacao de presenca | - |

Fluxo:

Quem faz o que: o **medico** cadastra os horarios disponiveis da propria agenda; o **atendente** (secretaria/recepcao) informa os horarios livres ao paciente e marca, remarca e cancela as consultas, alem de confirmar a presenca e registrar a falta; o **paciente** apenas acompanha os proprios agendamentos e consultas realizadas (marcar, remarcar ou cancelar e com o atendimento). A conta `admin` tem tambem o perfil de atendente.

1. **Cadastro de horario** (`/agenda/form`, so `MEDICO`): o medico cadastra apenas a propria agenda; o medico vem da conta, nao do formulario. Cada horario tem uma duracao (10 a 240 min, padrao 30). Nao aceita data/hora no passado, horario repetido nem que se sobreponha a outro do mesmo medico.
   - **Geracao em lote** (`/agenda/lote`): periodo de ate 92 dias, dias da semana, faixa de horario e duracao da consulta (10 a 240 min). Gera um horario por intervalo; a ultima consulta termina no maximo no horario final. Horarios que se sobrepoem a outros ja cadastrados ou que ja passaram sao ignorados, entao o mesmo lote pode ser gerado de novo. Tudo e gravado numa transacao, com limite de 1000 horarios por lote.
2. **Horarios disponiveis** (`/agenda/list`, equipe): lista paginada dos horarios `DISPONIVEL` que ainda nao passaram, com filtros por medico, especialidade e data. O botao "Agendar" so aparece para o atendente.
3. **Agendamento** (`/agendamentos/novo/{agendaId}` e `POST /agendamentos/save`, so `ATENDENTE`): o atendente escolhe o paciente; cria o `Agendamento` (`AGENDADO`) e muda a `Agenda` para `AGENDADO` na mesma transacao. O paciente nao pode ter outra consulta que se sobreponha, mesmo com outro medico (vale tambem no reagendamento). Nenhuma consulta e criada.
4. **Confirmacao de presenca** (`POST /agendamentos/confirmar/{id}`, so `ATENDENTE`): cria a `Consulta` e muda o agendamento para `REALIZADO`, na mesma transacao. So vale a partir de 1 hora antes do horario (`Agendamento.ANTECEDENCIA_CONFIRMACAO`). Uma segunda tentativa e rejeitada.
5. **Falta** (`POST /agendamentos/falta/{id}`, so `ATENDENTE`): depois do horario, marca o agendamento como `FALTOU`. Nenhuma consulta e criada e o horario, que ja passou, continua ocupado.
6. **Cancelamento** (`POST /agendamentos/cancelar/{id}`, so `ATENDENTE`, a qualquer momento antes do atendimento, ex.: o paciente avisou ou o medico nao pode atender): o agendamento vira `CANCELADO` (nao e excluido) e a agenda volta para `DISPONIVEL`. Ficam registrados quem cancelou (login), quando e o motivo, que e opcional (ate 255 caracteres). Um novo agendamento do mesmo horario cria outro registro. Agendamento `REALIZADO` ou `FALTOU` nao pode ser cancelado.
   - **Reagendamento** (`/agendamentos/reagendar/{id}`, so `ATENDENTE`): troca o horario por outro livre do mesmo medico numa unica transacao. O agendamento atual fica `CANCELADO` com o motivo "Reagendado para ..." e nasce um novo `AGENDADO` para o mesmo paciente. Se o novo horario nao estiver livre, nada muda.
   - **Agenda do dia** (`/agenda/dia`, equipe): todos os horarios de uma data agrupados por medico, livres e ocupados, com o paciente, a situacao, um resumo (livres, aguardando, realizados, faltas) e, para o atendente, as acoes. O medico ve so a propria agenda. Depois de uma acao feita ali, a tela volta para o mesmo dia.
7. **Historico** (`/consultas/list`, `/usuario/consultas`, `/usuario/agendamentos`): consultas registradas e, so para leitura, os agendamentos e consultas de quem esta autenticado. Na consulta so as observacoes sao editaveis, e apenas pelo medico que atendeu; alem dele, so o proprio paciente as le. O resto da equipe ve que a consulta aconteceu, sem as observacoes.

Integridade e concorrencia:

- Restricoes unicas no banco: `agendas (medico_id, data, horario)`, `consultas (agendamento_id)` e `agendamentos (agenda_id, reserva_ativa)`.
- "No maximo um agendamento ativo por horario" usa a coluna `reserva_ativa` (e nao um indice unico parcial, que o H2 dos testes nao tem): `TRUE` enquanto o agendamento ocupa o horario e `NULL` depois de cancelado. Como o banco nao trata dois `NULL` como iguais, cabem varios cancelados e um unico ativo por horario.
- Cada escrita comeca lendo com bloqueio pessimista (`SELECT ... FOR UPDATE`, `@Lock(PESSIMISTIC_WRITE)`) o registro que decide a regra: a `Agenda` e depois o `Paciente` ao agendar; o `Agendamento`, a nova `Agenda` e depois o `Paciente` ao reagendar; o `Agendamento` ao cancelar, confirmar ou registrar falta; o `Medico` ao cadastrar horarios, um a um ou em lote. Essa ordem deve ser mantida, para nao haver impasse (deadlock). Dois pacientes disputando o mesmo horario sao atendidos em fila e o segundo recebe "Este horario nao esta mais disponivel". As restricoes unicas ficam como segunda barreira.
- As regras ficam nos servicos (`AgendaService`, `AgendamentoService`), que lancam `RegraNegocioException`. O `RegraNegocioAdvice` (`@ControllerAdvice`) transforma a excecao em mensagem de erro e redireciona para a listagem; no formulario de horario ela aparece no campo correspondente.
- Medico com horarios na agenda e paciente com agendamentos nao podem ser excluidos, para preservar o historico.
- As regras de horario leem a hora por um `java.time.Clock` (bean em `config.RelogioConfig`), e nao por `LocalDateTime.now()` direto, para que os testes possam fixar o "agora". Na tela, os botoes de confirmar, registrar falta e cancelar so ficam habilitados quando a acao cabe no status e no horario; o servico confere tudo de novo.
- Os dados de demonstracao (`db/demo`) incluem um agendamento da vespera da migracao ainda pendente (Ana com o Dr. Eduardo), para demonstrar a confirmacao de presenca e o registro de falta.

## Seguranca e LGPD

Detalhes em `SECURITY.md`. Em resumo:

- **Bloqueio por tentativas**: 5 senhas erradas seguidas bloqueiam a conta por 15 minutos.
- **Politica de senha**: 8 a 72 caracteres, letras e numeros, sem conter o usuario.
- **Senhas**: cada um troca a propria em "Meus dados"; quem esqueceu recebe do ADMIN (`/contas/list`) uma senha provisoria, que precisa ser trocada no proximo acesso. A troca encerra as outras sessoes da conta.
- **Cabecalhos de seguranca**: Content-Security-Policy sem scripts inline, Referrer-Policy, Permissions-Policy, alem dos padroes do Spring Security.
- **Auditoria** (`/auditoria`, so ADMIN): quem fez o que e quando, gravada na mesma transacao da operacao.
- **LGPD**: aceite obrigatorio da politica de privacidade (`/privacidade`) no cadastro, com data registrada; o paciente baixa os proprios dados em JSON em "Meus dados".

## Spring Data JPA

Implementacao alinhada ao [capitulo de JPA](https://fagno.github.io/pwebii-spring-ifto/jpa/#_spring_data_jpa) da disciplina.

- Os repositorios sao interfaces que estendem `JpaRepository<Entidade, Long>`, sem `@Repository` e sem redeclarar metodos herdados.
- A maior parte das consultas e derivada do nome do metodo: `findAllByOrderByDataRealizacaoDesc`, `findByAgendamentoPacienteIdOrderByDataRealizacaoDesc`, `existsByMedicoIdAndDataAndHorario`, `existsByCpf`, `existsByCrm`... As listagens com filtros opcionais e paginacao (`AgendaRepository.buscarFuturos`, `AgendamentoRepository.buscar`) usam `@Query` com `Pageable`.
- Os relacionamentos sao `LAZY` e `spring.jpa.open-in-view` esta desligado; as listagens usam `@EntityGraph` ou `join fetch` para carregar medico e paciente na mesma consulta, sem N+1.
- As listas tem ordem definida: medicos e pacientes por nome (`Sort.by("nome").and(Sort.by("id"))`) e consultas da mais recente para a mais antiga.
- Os servicos (`PacienteService`, `MedicoService`, `AgendaService`, `AgendamentoService`, `ConsultaService`) usam `@Service`, injecao pelo construtor e o `@Transactional` do Spring: `readOnly = true` na classe para as leituras e `@Transactional` nos metodos de escrita.
- Na edicao, o servico busca a entidade e altera pelos setters, sem chamar `save()`: dentro da transacao a entidade esta gerenciada e as alteracoes sao gravadas no commit.
- Registro inexistente lanca `RegistroNaoEncontradoException`, que responde 404 com a pagina `templates/error/404.html`. Isso vale para edicao, exclusao e listagem de consultas por paciente ou medico.
- Excluir um paciente ou medico exclui tambem a conta de acesso dele (cascade da entidade), desde que nao tenha historico na agenda.

## Validacao

Implementacao alinhada ao [capitulo de validacao](https://fagno.github.io/pwebii-spring-ifto/validacao/) da disciplina. Toda a validacao acontece no servidor; os formularios usam `novalidate` e nao tem regras no HTML.

- Erros de preenchimento reapresentam o formulario com os valores digitados, o campo destacado (`th:errorclass="is-invalid"`) e a mensagem abaixo dele (`th:errors`). Apos salvar, a listagem mostra a mensagem de sucesso enviada por `addFlashAttribute`.
- Paciente: nome, CPF e data de nascimento (no passado) obrigatorios. O CPF e conferido pelo `@CPF` do Hibernate Validator (digitos verificadores) e nao pode se repetir.
- Medico: nome, CRM e especialidade obrigatorios. O CRM exige uma UF brasileira e um numero de ate seis digitos, e nao pode se repetir.
- E-mail e telefone sao opcionais; o e-mail deve ser valido quando preenchido.
- Horario da agenda: medico, data e horario obrigatorios; data e hora nao podem estar no passado nem repetir um horario do mesmo medico.
- Agendamento: horario e paciente obrigatorios. Disponibilidade e transicoes de status sao conferidas no servico.
- Consulta: as observacoes tem no maximo 500 caracteres.
- `messages.properties` traduz os erros de conversao do Spring MVC (data invalida, id nao numerico).

### Formato de CPF e CRM

- O CPF e sempre gravado como `000.000.000-00` e o CRM como `CRM-UF 0000`, qualquer que seja a forma digitada. A padronizacao e feita no servidor, nos setters `setCpf` e `setCrm`, antes da validacao e da checagem de duplicidade.
- No navegador, `static/js/mascaras.js` apenas formata o campo enquanto o usuario digita (atributo `data-mascara`); ele nao valida nada.
