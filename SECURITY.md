# Spring Security

The application authenticates users stored in the JPA tables `usuarios`, `perfis`
and `usuario_perfil`. Passwords are encoded with BCrypt. Profile names are kept
without the `ROLE_` prefix; Spring Security adds it (`ADMIN` -> `ROLE_ADMIN`).

## Profiles

| Profile    | Who                                   | How the account is created                  |
|------------|---------------------------------------|---------------------------------------------|
| `ADMIN`    | Clinic administration                 | `InitialUsersInitializer`                   |
| `USER`     | Reception                             | `InitialUsersInitializer`                   |
| `MEDICO`   | Doctor (institutional account)        | By an `ADMIN`, in the doctor registration   |
| `PACIENTE` | Patient                               | By the patient, in the public registration  |

The constants live in `Perfil` (`Perfil.ADMIN`, `Perfil.PACIENTE`, ...).

## People and accounts

`Paciente` and `Medico` each have an optional one-to-one `usuario` (column
`usuario_id`). Registering a patient or a doctor asks for a username and a
password and creates the `Usuario` in the same transaction, with `PACIENTE` or
`MEDICO` as the initial profile (`PacienteService.cadastrar`,
`MedicoService.cadastrar`, `UsuarioService.criar`).

- The form fields are bound to `Validation.Credenciais`, never to the `Usuario`
  entity. The profile is chosen by the server, not by the request.
- `CredenciaisValidator` rejects a username that is already taken.
- Editing a patient or a doctor does not change the account.
- Removing a patient or a doctor removes the account too.

## Initial users

`import.sql` runs first, when Hibernate creates the schema. It inserts the four
profiles and one account for each sample patient and doctor, already linked
through `usuario_id`:

- Patients (`PACIENTE`): `ana.souza`, `carlos.lima`, `mariana.alves`, all with
  the password `Paciente123`
- Doctors (`MEDICO`): `beatriz.rocha`, `eduardo.melo`, both with the password
  `Medico123`

The `password` column in `import.sql` holds BCrypt hashes generated with the
application's `BCryptPasswordEncoder`. To change a password there, generate a new
hash with the same encoder; plain text in that column will not authenticate.

Then `InitialUsersInitializer` creates whatever is still missing: the staff
accounts (and the profiles, if `import.sql` stops inserting them):

- Admin: `admin` / `Admin123` (profiles `ADMIN` and `USER`)
- Reception: `user` / `User123` (profile `USER`)

These are development credentials only. The integration tests log in with them,
so change the tests together.

The current datasource is an in-memory H2 database and the schema is recreated
on each run (`spring.jpa.hibernate.ddl-auto=create-drop`), so every account,
including the ones created through the forms, is lost on restart, and
remember-me cookies stop being valid.

## Access rules

- Public: `/login`, static CSS/JavaScript/images, `/error`, and the patient
  registration (`GET /pacientes/form`, `POST /pacientes/save`).
- Any authenticated user: `/`, `/usuario/**` (own data, own bookings and own
  appointments), the available time slots (`GET /agenda/list`), booking a slot
  (`GET /agendamentos/novo/{agendaId}`, `POST /agendamentos/save`) and
  cancelling a booking (`POST /agendamentos/cancelar/{id}`).
- `ADMIN`, `USER` and `MEDICO`: patients, the list of doctors, registering time
  slots (`/agenda/form`, `POST /agenda/save`, and in batch: `/agenda/lote`), the
  booking management page (`/agendamentos/list`) and the appointment history
  (`/consultas/**`).
- `ADMIN` and `USER` only: confirming a patient's attendance
  (`POST /agendamentos/confirmar/{id}`), which is what creates the `Consulta`, and
  recording a no-show (`POST /agendamentos/falta/{id}`).
- `ADMIN` only: add/edit/remove doctors, remove patients, and the H2 console.
- A `PACIENTE` cannot open the patient, doctor, booking management or
  appointment history pages, so one patient never sees another patient's data.
  `/usuario/consultas` and `/usuario/agendamentos` take no id: they list what
  belongs to whoever is logged in.
- Ownership is enforced on the server, not by the forms:
  - a `PACIENTE` always books for the patient linked to the account; a
    `pacienteId` sent in the request is ignored (`AgendamentoController.save`);
  - a `PACIENTE` can only cancel their own booking, and only before its time; for
    anyone else's the answer is `404`, the same as for a booking that does not
    exist (`AgendamentoService.cancelarDoPaciente`);
  - a `MEDICO` can only register slots in their own schedule, one by one or in
    batch; a `medicoId` sent in the request is ignored (`AgendaController.save`,
    `AgendaController.gerarLote`).
- Buttons and menu items are hidden with `sec:authorize` according to the
  profile; the URL rules in `SecurityConfiguration` are what enforce access.
- A user without the required profile gets `403` (`templates/error/403.html`).

## Login, logout and HTTP Basic

- Form login at `/login`. After signing in, the user goes back to the page that
  was requested, or to `/` when there was none.
- Logout is a CSRF-protected POST to `/logout` and redirects to `/login?logout`.
- HTTP Basic is also enabled, for tools such as curl:
  `curl -u user:User123 http://localhost:8080/pacientes/list`.
  Unauthenticated browsers (requests that accept HTML) are still redirected to
  `/login`; other clients receive `401` with a `WWW-Authenticate: Basic` header.
- `/usuario/dados` shows the authenticated user's login, status, profiles and
  the patient or doctor the account belongs to (`UsuarioController`, using
  `@AuthenticationPrincipal`).

## CSRF

Removals (`/medicos/remove/{id}`, `/pacientes/remove/{id}`) and the booking
actions (`/agendamentos/save`, `/agendamentos/cancelar/{id}`,
`/agendamentos/confirmar/{id}`, `/agendamentos/falta/{id}`) are POST requests sent by forms with
`th:action`, so they carry the CSRF token like every other form. A GET to those
URLs answers `405`.

The CSRF exception is limited to the H2 console, which does not submit Spring's
token.
