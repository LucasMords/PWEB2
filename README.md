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
