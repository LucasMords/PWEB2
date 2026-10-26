# Sistema de Consultas - PWEB II

Aplicacao Spring Boot para gerenciamento de pacientes, medicos e consultas. Demonstra associacoes JPA bidirecionais e CRUD com Spring MVC e Thymeleaf.

## Tecnologias e modelo

- Java 25, Spring Boot, Spring Web MVC e Thymeleaf
- Spring Data JPA / Hibernate e H2 em memoria
- `Paciente 1:N Consulta` e `Medico 1:N Consulta`
- Toda consulta possui um paciente e um medico obrigatorios

As tabelas sao recriadas ao iniciar e preenchidas por `src/main/resources/import.sql`.

## Execucao

```cmd
mvnw.cmd spring-boot:run
```

Abra `http://localhost:8080`. O console H2 fica em `/h2-console` (URL `jdbc:h2:mem:clinica`, usuario `sa`, sem senha).

Testes: `mvnw.cmd test`.

## Validacao e integridade

- Cadastros e edicoes validam os dados no servidor e retornam ao formulario com os valores preenchidos e mensagens de erro.
- Nomes e especialidade sao obrigatorios; CPF possui verificacao dos digitos; nascimento nao pode ser futuro. E-mail e telefone sao opcionais, mas devem ser validos quando preenchidos.
- CPF e telefone sao armazenados sem mascara. CRM e normalizado para maiusculas e deve seguir `CRM-TO 123456`, com uma UF brasileira e de um a seis digitos. CPF e CRM nao podem se repetir, inclusive na edicao de outro registro.
- Novas consultas iniciam como AGENDADA e exigem data futura, paciente e medico existentes. Reagendamentos tambem exigem data futura. Consultas anteriores ao nascimento do paciente sao rejeitadas.
- Medico e paciente nao podem ter duas consultas nao canceladas no mesmo instante. A verificacao usa bloqueios transacionais dos cadastros para serializar agendamentos concorrentes. Como nao existe duracao no modelo, nao ha verificacao de sobreposicao de intervalos.
- AGENDADA pode passar para REALIZADA ou CANCELADA. Uma consulta futura nao pode ser realizada. Consultas finalizadas permitem somente alterar observacoes. Consultas antigas agendadas podem receber atualizacao de status sem exigir reagendamento.
- Exclusoes usam POST. Pacientes e medicos com consultas vinculadas nao podem ser excluidos; consultas realizadas tambem nao podem ser excluidas. Canceladas podem ser excluidas e liberam o horario.
- Identificadores invalidos nas URLs retornam 400, registros inexistentes retornam 404 e conflitos de integridade retornam 409. Nos formularios, identificadores ausentes, invalidos ou enviados indevidamente no cadastro reapresentam o formulario com erros por campo.
- As buscas existentes sao por paciente ou medico e validam o identificador. Nao existem filtros livres por periodo, texto ou status.
- O H2 continua em memoria e os dados sao recriados ao reiniciar, conforme a configuracao didatica do projeto.

## Modelo de validacao da disciplina

Implementacao alinhada ao [capitulo de validacao](https://fagno.github.io/pwebii-spring-ifto/validacao/).

- As entidades usam Jakarta Validation. Os controllers usam `@ModelAttribute` e `@Validated`, com `BindingResult` imediatamente apos o objeto, e recebem suas dependencias pelo construtor. `@Transactional` pertence ao Spring.
- `Default` mantem as regras comuns. `Cadastro` rejeita ID informado (`@Null`); `Edicao` exige ID positivo (`@NotNull` e `@Positive`). Cada endpoint seleciona `Default` e somente o grupo de sua operacao.
- Na consulta, `@Valid` e `@ConvertGroup` validam paciente e medico no grupo `Referencia`. O formulario seleciona cadastros existentes: exige seus IDs, sem exigir os demais campos de um novo cadastro. O controller verifica a existencia dos registros.
- `ValidationMessages.properties` contem as mensagens das restricoes, com interpolacao como `{max}`; `messages.properties` personaliza falhas de conversao do Spring MVC.
- Erros de preenchimento retornam a mesma view antes de consultar ou gravar dados. Erros de negocio tambem reapresentam o formulario. Os templates usam `th:errors`, `th:errorclass` e `#fields.detailedErrors()`.
- Nao ha camada de servico nesta fase. Cada controller concentra as regras de sua funcionalidade em metodos privados e acessa os repositorios diretamente, dentro da transacao. Ele grava apenas apos as verificacoes, redireciona e usa `addFlashAttribute` para apresentar a mensagem de sucesso na listagem.
- Datas de agendamento sao verificadas no controller: uma restricao `@Future` incondicional impediria editar observacoes de consultas historicas.
- `@InitBinder` e `setAllowedFields` limitam quais propriedades o formulario pode preencher. Em consultas, por exemplo, `paciente.id` e permitido, mas `paciente.nome` e ignorado. Essa lista controla a vinculacao dos campos; as anotacoes e as regras do controller validam seus valores.

Os testes cobrem restricoes, grupos, validacao em cascata, conversao, preservacao dos campos, mensagens flash, integridade e agendamentos simultaneos.
