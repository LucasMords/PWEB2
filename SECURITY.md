# Spring Security

The application authenticates users stored in the JPA tables `usuarios`, `perfis`
and `usuario_perfil`. Passwords are encoded with BCrypt. Profile names are kept
without the `ROLE_` prefix; Spring Security adds it (`ADMIN` -> `ROLE_ADMIN`).

## Profiles

| Profile    | Who                                   | How the account is created                  |
|------------|---------------------------------------|---------------------------------------------|
| `ADMIN`    | Clinic administration                 | `InitialUsersInitializer`                   |
| `ATENDENTE`| Secretary / front desk: books the appointments | `InitialUsersInitializer`          |
| `MEDICO`   | Doctor (institutional account)        | By an `ADMIN`, in the doctor registration   |
| `PACIENTE` | Patient (read-only access to their own bookings and appointments) | By the patient, in the public registration |

Division of work: the doctor publishes the available time slots of their own
schedule; the `ATENDENTE` tells the patient which slots are free and books,
reschedules or cancels the appointment, and records attendance or no-show; the
patient only follows their bookings and past appointments.

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

The Flyway migration `V1__esquema_inicial.sql` inserts the four profiles. In
development and in the tests, the demo migration (`db/demo`) also inserts one
account for each sample patient and doctor, already linked through `usuario_id`:

- Patients (`PACIENTE`): `ana.souza`, `carlos.lima`, `mariana.alves`, all with
  the password `Paciente123`
- Doctors (`MEDICO`): `beatriz.rocha`, `eduardo.melo`, both with the password
  `Medico123`

The `password` column in the demo migration holds BCrypt hashes generated with
the application's `BCryptPasswordEncoder`; plain text in that column will not
authenticate. The `prod` profile never loads the demo data.

Then `InitialUsersInitializer` creates the staff accounts the first time the
application starts on a new database, and does nothing afterwards (so a password
changed later is not reset):

- Admin: `admin` (profiles `ADMIN` and `ATENDENTE`), password from
  `CLINICA_SENHA_ADMIN`
- Front desk: `atendente` (profile `ATENDENTE`), password from
  `CLINICA_SENHA_ATENDENTE`; an empty value means the account is not created

In development the defaults are `Admin123` and `Atendente123`; the integration
tests log in with them. The `prod` profile has no defaults: without
`CLINICA_SENHA_ADMIN` the application does not start.

## Production profile

`application-prod.properties` (active in the Docker image) loads only the schema
migrations and turns off SQL logging, which could leak patient data. It marks the
session cookie `Secure` and trusts the `X-Forwarded-*` headers of the reverse
proxy that terminates HTTPS, so redirects and HSTS use `https`. The container
runs as a non-root user.

## Database credentials

The application connects to PostgreSQL with its own role (`clinica`, owner of the
`clinica` database), never with the `postgres` superuser. The URL, user and
password come from the environment (`DB_URL`, `DB_USUARIO`, `DB_SENHA`); no
password is stored in the repository. Docker Compose reads them from a `.env`
file, which is ignored by git (`.env.exemplo` documents the variables). Database
backups (`backups/`) contain personal and health data: they are ignored by git
and must be stored with the same care as the database.

## Access rules

- Public: `/login`, static CSS/JavaScript/images, `/error`, the privacy policy
  (`/privacidade`), the patient registration (`GET /pacientes/form`,
  `POST /pacientes/save`) and the health check (`GET /actuator/health`, plus
  `/liveness` and `/readiness`). Anonymous callers only get the status; the
  details (database, disk) are shown to `ADMIN` only. No other Actuator endpoint
  is exposed over HTTP, and `/actuator/**` requires `ADMIN` in case one ever is.
- Any authenticated user: `/` and `/usuario/**` (own data, and read-only lists of
  own bookings and own appointments).
- `MEDICO` only: registering time slots (`/agenda/form`, `POST /agenda/save`, and
  in batch: `/agenda/lote`). Neither `ADMIN` nor `ATENDENTE` can.
- `ATENDENTE` only: booking a slot for a patient
  (`GET /agendamentos/novo/{agendaId}`, `POST /agendamentos/save`), cancelling
  (`POST /agendamentos/cancelar/{id}`), rescheduling
  (`/agendamentos/reagendar/{id}`), confirming attendance
  (`POST /agendamentos/confirmar/{id}`), which is what creates the `Consulta`, and
  recording a no-show (`POST /agendamentos/falta/{id}`). The `admin` account has
  this profile too.
- `ADMIN`, `ATENDENTE` and `MEDICO`: patients, the list of doctors, the available
  time slots (`/agenda/list`), the booking management page (`/agendamentos/list`),
  the day schedule (`/agenda/dia`; a `MEDICO` only sees their own) and the
  appointment history (`/consultas/**`).
- `ADMIN` only: add/edit/remove doctors, remove patients, the audit log
  (`/auditoria`).
- A `PACIENTE` cannot open the patient, doctor, time slot, booking management or
  appointment history pages, nor book, cancel or reschedule (`403`), so one
  patient never sees or changes another patient's data.
  `/usuario/consultas` and `/usuario/agendamentos` take no id: they list what
  belongs to whoever is logged in.
- Ownership is enforced on the server, not by the forms: a `MEDICO` can only
  register slots in their own schedule, one by one or in batch; a `medicoId` sent
  in the request is ignored (`AgendaController.save`, `AgendaController.gerarLote`).
- Who cancelled (login) and when is stored with the booking; staff see it,
  patients only see the reason.
- Buttons and menu items are hidden with `sec:authorize` according to the
  profile; the URL rules in `SecurityConfiguration` are what enforce access.
- A user without the required profile gets `403` (`templates/error/403.html`).

## Brute-force protection and password policy

- After 5 wrong passwords in a row (`TentativasLoginService.LIMITE_TENTATIVAS`) the
  account is locked for 15 minutes (`DURACAO_BLOQUEIO`). While locked, not even the
  right password works: Spring Security checks the lock before the password
  (`UsuarioDetailsService` sets `accountLocked`). The lock ends by itself; a
  successful login resets the counter. Failures are counted under a pessimistic
  lock on the user row, so concurrent attempts are not lost.
- The counting is driven by Spring Security's authentication events
  (`EventosDeAutenticacao`, published by the `DefaultAuthenticationEventPublisher`
  bean in `SecurityConfiguration`), for both form login and HTTP Basic.
- The login page shows the same message for a wrong password, an unknown user and
  a locked account, so it does not reveal which accounts exist.
- Passwords need 8 to 72 characters, letters and digits, and must not contain the
  username (`Credenciais`, `CredenciaisValidator`).

## Appointment notes

Notes (`Consulta.observacoes`) are health data: only the doctor who attended the
patient edits them (`ConsultaService.buscarDoMedico`), and only that doctor and the
patient read them. Other staff see that the appointment happened, without the notes.

## Security headers

`SecurityConfiguration` sends, besides Spring Security's defaults
(`X-Content-Type-Options`, `X-Frame-Options: SAMEORIGIN`, cache control, and HSTS on
HTTPS responses):

- `Content-Security-Policy`: scripts, styles and fonts only from the application
  and from the Bootstrap CDN (`cdn.jsdelivr.net`), no inline script or style,
  `object-src 'none'`, `form-action 'self'`. The templates have no inline JavaScript
  (confirmations use `data-confirmar` and `static/js/confirmar.js`).
- `Referrer-Policy: strict-origin-when-cross-origin`.
- `Permissions-Policy` turning off camera, microphone, geolocation and payment.

## Audit log

`RegistroAuditoria` (table `auditoria`) records who did what, when and on which
record: logins through the form, wrong passwords, account locks, slot
registration, booking, cancelling, rescheduling, attendance, no-shows, changes to
appointment notes, patient and doctor registration/edits/removals and LGPD data
exports. `AuditoriaService.registrar` runs inside the caller's transaction
(`Propagation.MANDATORY`): if the operation is rolled back, so is the record.
Login events use `registrarSeparado` (own transaction). The detail identifies the
record (ids, dates) and never copies health data such as appointment notes. The
application only inserts audit rows; `/auditoria` (ADMIN only) lists them with
filters by user, action and period.

## LGPD

- Patient registration requires accepting the privacy policy (`/privacidade`,
  public). The time of acceptance is stored in `pacientes.consentimento_em`; the
  field has no setter, so the form cannot fill it. `PacienteService.cadastrar`
  refuses a registration without consent.
- `GET /usuario/dados/exportar`: a patient downloads, as JSON, everything the
  clinic stores about them (registration data, bookings including cancelled ones,
  appointments). There is no id in the URL: the patient is the one linked to the
  account. The response is `no-store` and the export is audited. Staff accounts
  get `404`.
- Deletion: patients with appointment history are not deleted on request, because
  medical records must be kept (Lei 13.787/2018). The policy page explains this.
  The policy text is a template: the clinic must review it and fill in the
  controller and data protection officer contacts before real use.

## Login, logout and HTTP Basic

- Form login at `/login`. After signing in, the user goes back to the page that
  was requested, or to `/` when there was none.
- Logout is a CSRF-protected POST to `/logout` and redirects to `/login?logout`.
- HTTP Basic is also enabled, for tools such as curl:
  `curl -u atendente:Atendente123 http://localhost:8080/pacientes/list`.
  Unauthenticated browsers (requests that accept HTML) are still redirected to
  `/login`; other clients receive `401` with a `WWW-Authenticate: Basic` header.
- `/usuario/dados` shows the authenticated user's login, status, profiles and
  the patient or doctor the account belongs to (`UsuarioController`, using
  `@AuthenticationPrincipal`).

## CSRF

Removals (`/medicos/remove/{id}`, `/pacientes/remove/{id}`) and the booking
actions (`/agendamentos/save`, `/agendamentos/cancelar/{id}`,
`/agendamentos/confirmar/{id}`, `/agendamentos/falta/{id}`,
`/agendamentos/reagendar/{id}`) are POST requests sent by forms with
`th:action`, so they carry the CSRF token like every other form. A GET to those
URLs answers `405`.

There are no CSRF exceptions.
