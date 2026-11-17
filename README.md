# Sistema de Consultas - PWEB II

Aplicacao Spring Boot para gerenciamento de pacientes, medicos e consultas. Demonstra associacoes JPA bidirecionais, repositorios Spring Data JPA, camada de servico transacional, Bean Validation e CRUD com Spring MVC e Thymeleaf.

## Tecnologias e modelo

- Java 25, Spring Boot, Spring Web MVC e Thymeleaf
- Spring Data JPA / Hibernate e H2 em memoria
- Bean Validation (Jakarta Validation / Hibernate Validator)
- Bootstrap 5.3 (via CDN) com fragmentos Thymeleaf para o layout
- `Medico 1:N Agenda`, `Agenda 1:N Agendamento`, `Paciente 1:N Agendamento` e `Agendamento 1:0..1 Consulta`
- A consulta so existe depois que a clinica confirma a presenca do paciente; paciente e medico dela vem do agendamento (veja "Agenda, agendamentos e consultas")

As tabelas sao recriadas ao iniciar e preenchidas por `src/main/resources/import.sql`.

## Execucao

```cmd
mvnw.cmd spring-boot:run
```

Abra `http://localhost:8080`. O console H2 fica em `/h2-console` (URL `jdbc:h2:mem:clinica`, usuario `sa`, sem senha).

Testes: `mvnw.cmd test`.

## Camadas

| Pacote | Papel |
|---|---|
| `Controller` | Recebe a requisicao, valida o formulario (`@Valid`, `BindingResult`), chama o servico e redireciona |
| `Service` | Regras e transacoes; unico ponto que acessa os repositorios |
| `Model.repository` | Interfaces Spring Data JPA |
| `Model.Entity` | Entidades JPA com as restricoes de validacao |
| `Dto` | Objetos dos formularios e dos filtros da agenda (`AgendaForm`, `AgendamentoForm`, `AgendaFiltro`...) |
| `Validation` | Credenciais do cadastro (`Credenciais`, `CredenciaisValidator`) |

## Agenda, agendamentos e consultas

| Entidade | O que e | Status |
|---|---|---|
| `Agenda` | Horario disponibilizado por um medico (medico, data, horario) | `DISPONIVEL`, `AGENDADO` |
| `Agendamento` | Reserva de um horario por um paciente | `AGENDADO`, `REALIZADO`, `CANCELADO` |
| `Consulta` | Atendimento realizado, criado na confirmacao de presenca | - |

Fluxo:

1. **Cadastro de horario** (`/agenda/form`): administracao e recepcao escolhem o medico; o medico cadastra apenas a propria agenda. Nao aceita data/hora no passado nem horario repetido para o mesmo medico.
2. **Horarios disponiveis** (`/agenda/list`): lista paginada dos horarios `DISPONIVEL` que ainda nao passaram, com filtros por medico, especialidade e data.
3. **Agendamento** (`/agendamentos/novo/{agendaId}` e `POST /agendamentos/save`): cria o `Agendamento` (`AGENDADO`) e muda a `Agenda` para `AGENDADO` na mesma transacao. Nenhuma consulta e criada. O paciente agenda sempre para si (a conta autenticada decide, nao o formulario); a equipe escolhe o paciente.
4. **Confirmacao de presenca** (`POST /agendamentos/confirmar/{id}`, so `ADMIN` e `USER`): cria a `Consulta` e muda o agendamento para `REALIZADO`, na mesma transacao. Uma segunda tentativa e rejeitada.
5. **Cancelamento** (`POST /agendamentos/cancelar/{id}`): o agendamento vira `CANCELADO` (nao e excluido) e a agenda volta para `DISPONIVEL`. Um novo agendamento do mesmo horario cria outro registro. Agendamento `REALIZADO` nao pode ser cancelado. O paciente so cancela os proprios agendamentos.
6. **Historico** (`/consultas/list`, `/usuario/consultas`, `/usuario/agendamentos`): consultas registradas e agendamentos de quem esta autenticado. Na consulta so as observacoes sao editaveis.

Integridade e concorrencia:

- Restricoes unicas no banco: `agendas (medico_id, data, horario)`, `consultas (agendamento_id)` e `agendamentos (agenda_id, reserva_ativa)`.
- O H2 nao tem indice unico parcial, entao "no maximo um agendamento ativo por horario" usa a coluna `reserva_ativa`: `TRUE` enquanto o agendamento ocupa o horario e `NULL` depois de cancelado. Como o banco nao trata dois `NULL` como iguais, cabem varios cancelados e um unico ativo por horario.
- Cada escrita comeca lendo com bloqueio pessimista (`SELECT ... FOR UPDATE`, `@Lock(PESSIMISTIC_WRITE)`) o registro que decide a regra: a `Agenda` ao reservar, o `Agendamento` ao cancelar ou confirmar. Dois pacientes disputando o mesmo horario sao atendidos em fila e o segundo recebe "Este horario nao esta mais disponivel". As restricoes unicas ficam como segunda barreira.
- As regras ficam nos servicos (`AgendaService`, `AgendamentoService`), que lancam `RegraNegocioException`. O `RegraNegocioAdvice` (`@ControllerAdvice`) transforma a excecao em mensagem de erro e redireciona para a listagem; no formulario de horario ela aparece no campo correspondente.
- Medico com horarios na agenda e paciente com agendamentos nao podem ser excluidos, para preservar o historico.

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
- Agendamento: horario obrigatorio e, para a equipe, paciente obrigatorio. Disponibilidade e transicoes de status sao conferidas no servico.
- Consulta: as observacoes tem no maximo 500 caracteres.
- `messages.properties` traduz os erros de conversao do Spring MVC (data invalida, id nao numerico).

### Formato de CPF e CRM

- O CPF e sempre gravado como `000.000.000-00` e o CRM como `CRM-UF 0000`, qualquer que seja a forma digitada. A padronizacao e feita no servidor, nos setters `setCpf` e `setCrm`, antes da validacao e da checagem de duplicidade.
- No navegador, `static/js/mascaras.js` apenas formata o campo enquanto o usuario digita (atributo `data-mascara`); ele nao valida nada.
