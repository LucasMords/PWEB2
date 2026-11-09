# Sistema de Consultas - PWEB II

Aplicacao Spring Boot para gerenciamento de pacientes, medicos e consultas. Demonstra associacoes JPA bidirecionais, repositorios Spring Data JPA, camada de servico transacional, Bean Validation e CRUD com Spring MVC e Thymeleaf.

## Tecnologias e modelo

- Java 25, Spring Boot, Spring Web MVC e Thymeleaf
- Spring Data JPA / Hibernate e H2 em memoria
- Bean Validation (Jakarta Validation / Hibernate Validator)
- Bootstrap 5.3 (via CDN) com fragmentos Thymeleaf para o layout
- `Paciente 1:N Consulta` e `Medico 1:N Consulta`
- Toda consulta possui um paciente e um medico obrigatorios

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
| `Validation` | Grupos de validacao (`Cadastro`) |

## Spring Data JPA

Implementacao alinhada ao [capitulo de JPA](https://fagno.github.io/pwebii-spring-ifto/jpa/#_spring_data_jpa) da disciplina.

- Os repositorios sao interfaces que estendem `JpaRepository<Entidade, Long>`, sem `@Repository` e sem redeclarar metodos herdados.
- As consultas sao derivadas do nome do metodo: `findAllByOrderByDataHoraDesc`, `findByPacienteIdOrderByDataHoraDesc`, `findByMedicoIdOrderByDataHoraDesc`, `existsByCpf`, `existsByCpfAndIdNot`, `existsByCrm` e `existsByCrmAndIdNot`.
- `paciente` e `medico` da consulta sao `LAZY` e `spring.jpa.open-in-view` esta desligado; as listagens de consultas usam `@EntityGraph` para carregar os dois na mesma consulta.
- As listas tem ordem definida: medicos e pacientes por nome (`Sort.by("nome").and(Sort.by("id"))`) e consultas da mais recente para a mais antiga.
- Os servicos (`PacienteService`, `MedicoService`, `ConsultaService`) usam `@Service`, injecao pelo construtor e o `@Transactional` do Spring: `readOnly = true` na classe para as leituras e `@Transactional` nos metodos de escrita.
- Na edicao, o servico busca a entidade e altera pelos setters, sem chamar `save()`: dentro da transacao a entidade esta gerenciada e as alteracoes sao gravadas no commit.
- Registro inexistente lanca `RegistroNaoEncontradoException`, que responde 404 com a pagina `templates/error/404.html`. Isso vale para edicao, exclusao e listagem de consultas por paciente ou medico.
- Excluir um paciente ou medico exclui tambem as consultas dele (cascade da entidade).

## Validacao

Implementacao alinhada ao [capitulo de validacao](https://fagno.github.io/pwebii-spring-ifto/validacao/) da disciplina. Toda a validacao acontece no servidor; os formularios usam `novalidate` e nao tem regras no HTML.

- Erros de preenchimento reapresentam o formulario com os valores digitados, o campo destacado (`th:errorclass="is-invalid"`) e a mensagem abaixo dele (`th:errors`). Apos salvar, a listagem mostra a mensagem de sucesso enviada por `addFlashAttribute`.
- Paciente: nome, CPF e data de nascimento (no passado) obrigatorios. O CPF e conferido pelo `@CPF` do Hibernate Validator (digitos verificadores) e nao pode se repetir.
- Medico: nome, CRM e especialidade obrigatorios. O CRM exige uma UF brasileira e um numero de ate seis digitos, e nao pode se repetir.
- E-mail e telefone sao opcionais; o e-mail deve ser valido quando preenchido.
- Consulta: paciente, medico e data/hora obrigatorios. A data nao pode estar no passado apenas no cadastro (grupo `Cadastro`), para que consultas ja ocorridas possam ser editadas.
- Toda consulta nova comeca como AGENDADA; o status so pode ser alterado na edicao.
- `messages.properties` traduz os erros de conversao do Spring MVC (data invalida, id nao numerico).

### Formato de CPF e CRM

- O CPF e sempre gravado como `000.000.000-00` e o CRM como `CRM-UF 0000`, qualquer que seja a forma digitada. A padronizacao e feita no servidor, nos setters `setCpf` e `setCrm`, antes da validacao e da checagem de duplicidade.
- No navegador, `static/js/mascaras.js` apenas formata o campo enquanto o usuario digita (atributo `data-mascara`); ele nao valida nada.
