-- Dados de demonstracao: so em desenvolvimento e nos testes (spring.flyway.locations inclui
-- db/demo); o perfil prod nao carrega esta pasta. Roda uma unica vez, logo depois do V1.
-- O SQL e o mesmo no PostgreSQL e no H2 dos testes; os ids sao buscados pelo CRM, CPF ou
-- login, nunca fixados.
--
-- As datas "relativas" (amanha, ontem) valem para o dia em que a migracao rodou.

-- Contas dos pacientes e medicos. A coluna password guarda o hash BCrypt, gerado com o mesmo
-- BCryptPasswordEncoder da aplicacao; a senha em texto nunca vai para o banco.
-- Senha dos pacientes: Paciente123. Senha dos medicos: Medico123.
insert into usuarios (login, password, ativo) values ('ana.souza', '$2a$10$2qLqzcexlu/dh6JHCvC2vesH.RC7zdPT8bMj8EEc49yTZy/.cmY1W', true);
insert into usuarios (login, password, ativo) values ('carlos.lima', '$2a$10$4QRiqPPwBem3KYjhT8k8UO0HvqHvUfGMZAT/wYXX4b2/weA2U4fo2', true);
insert into usuarios (login, password, ativo) values ('mariana.alves', '$2a$10$yaOZYQPQBDilA74JXRhezuPlTrctYoV21etGiwwjnUizHS/RdT5KW', true);
insert into usuarios (login, password, ativo) values ('beatriz.rocha', '$2a$10$XT//5CS1NStxv2VSu.062OSSg5IciBP9DnFUhCBZspe3r8oysNGXq', true);
insert into usuarios (login, password, ativo) values ('eduardo.melo', '$2a$10$q507SAVwbBQYF5RbT91FK.UijHADdlSfgNEOuSFXxOu6LnHaLS/TW', true);

insert into usuario_perfil (usuario_id, perfil_id)
select u.id, p.id from usuarios u, perfis p
where p.nome = 'PACIENTE' and u.login in ('ana.souza', 'carlos.lima', 'mariana.alves');
insert into usuario_perfil (usuario_id, perfil_id)
select u.id, p.id from usuarios u, perfis p
where p.nome = 'MEDICO' and u.login in ('beatriz.rocha', 'eduardo.melo');

insert into pacientes (nome, cpf, data_nascimento, telefone, email, consentimento_em, usuario_id)
values ('Ana Souza', '529.982.247-25', '1992-05-14', '(63) 99999-1001', 'ana@email.com', '2026-08-01 09:00:00', (select id from usuarios where login = 'ana.souza'));
insert into pacientes (nome, cpf, data_nascimento, telefone, email, consentimento_em, usuario_id)
values ('Carlos Lima', '123.456.789-09', '1985-09-22', '(63) 99999-1002', 'carlos@email.com', '2026-08-01 09:00:00', (select id from usuarios where login = 'carlos.lima'));
insert into pacientes (nome, cpf, data_nascimento, telefone, email, consentimento_em, usuario_id)
values ('Mariana Alves', '987.654.321-00', '2000-01-10', '(63) 99999-1003', 'mariana@email.com', '2026-08-01 09:00:00', (select id from usuarios where login = 'mariana.alves'));

insert into medicos (nome, crm, especialidade, telefone, email, usuario_id)
values ('Dra. Beatriz Rocha', 'CRM-TO 1001', 'Cardiologia', '(63) 99999-2001', 'beatriz@clinica.com', (select id from usuarios where login = 'beatriz.rocha'));
insert into medicos (nome, crm, especialidade, telefone, email, usuario_id)
values ('Dr. Eduardo Melo', 'CRM-TO 1002', 'Clinica Geral', '(63) 99999-2002', 'eduardo@clinica.com', (select id from usuarios where login = 'eduardo.melo'));

-- Historico: tres atendimentos ja realizados. Cada um tem o horario na agenda do medico
-- (AGENDADO), o agendamento do paciente (REALIZADO) e a consulta da confirmacao de presenca.
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1002'), '2026-08-25', '09:00:00', 'AGENDADO');
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1001'), '2026-09-03', '14:30:00', 'AGENDADO');
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1002'), '2026-09-05', '10:00:00', 'AGENDADO');

insert into agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa)
values ((select a.id from agendas a join medicos m on m.id = a.medico_id where m.crm = 'CRM-TO 1002' and a.data = '2026-08-25' and a.horario = '09:00:00'),
        (select id from pacientes where cpf = '529.982.247-25'), '2026-08-18 10:15:00', 'REALIZADO', true);
insert into agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa)
values ((select a.id from agendas a join medicos m on m.id = a.medico_id where m.crm = 'CRM-TO 1001' and a.data = '2026-09-03' and a.horario = '14:30:00'),
        (select id from pacientes where cpf = '529.982.247-25'), '2026-08-27 16:40:00', 'REALIZADO', true);
insert into agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa)
values ((select a.id from agendas a join medicos m on m.id = a.medico_id where m.crm = 'CRM-TO 1002' and a.data = '2026-09-05' and a.horario = '10:00:00'),
        (select id from pacientes where cpf = '123.456.789-09'), '2026-08-29 09:05:00', 'REALIZADO', true);

insert into consultas (agendamento_id, data_realizacao, observacoes)
select ag.id, '2026-08-25 09:00:00', 'Consulta de rotina.' from agendamentos ag join agendas a on a.id = ag.agenda_id where a.data = '2026-08-25' and a.horario = '09:00:00';
insert into consultas (agendamento_id, data_realizacao, observacoes)
select ag.id, '2026-09-03 14:30:00', 'Retorno para avaliacao de exames.' from agendamentos ag join agendas a on a.id = ag.agenda_id where a.data = '2026-09-03' and a.horario = '14:30:00';
insert into consultas (agendamento_id, data_realizacao, observacoes)
select ag.id, '2026-09-05 10:00:00', 'Primeira consulta.' from agendamentos ag join agendas a on a.id = ag.agenda_id where a.data = '2026-09-05' and a.horario = '10:00:00';

-- Agenda futura, a partir do dia da migracao, para que haja horarios livres.
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1001'), cast(current_date + interval '1' day as date), '08:00:00', 'DISPONIVEL');
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1001'), cast(current_date + interval '1' day as date), '09:00:00', 'DISPONIVEL');
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1001'), cast(current_date + interval '2' day as date), '14:00:00', 'DISPONIVEL');
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1002'), cast(current_date + interval '1' day as date), '10:30:00', 'DISPONIVEL');
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1002'), cast(current_date + interval '3' day as date), '15:00:00', 'DISPONIVEL');

-- Um horario ja reservado (Mariana com a Dra. Beatriz): aguarda a confirmacao de presenca.
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1001'), cast(current_date + interval '2' day as date), '10:00:00', 'AGENDADO');
insert into agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa)
values ((select a.id from agendas a join medicos m on m.id = a.medico_id
         where m.crm = 'CRM-TO 1001' and a.data = cast(current_date + interval '2' day as date) and a.horario = '10:00:00'),
        (select id from pacientes where cpf = '987.654.321-00'), current_timestamp, 'AGENDADO', true);

-- Um horario de ontem ainda AGENDADO (Ana com o Dr. Eduardo): o atendimento pode confirmar
-- a presenca ou registrar a falta.
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1002'), cast(current_date - interval '1' day as date), '16:00:00', 'AGENDADO');
insert into agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa)
values ((select a.id from agendas a join medicos m on m.id = a.medico_id
         where m.crm = 'CRM-TO 1002' and a.data = cast(current_date - interval '1' day as date) and a.horario = '16:00:00'),
        (select id from pacientes where cpf = '529.982.247-25'), current_timestamp - interval '3' day, 'AGENDADO', true);

-- Uma falta no historico (Carlos com a Dra. Beatriz): o horario segue ocupado, sem consulta.
insert into agendas (medico_id, data, horario, status)
values ((select id from medicos where crm = 'CRM-TO 1001'), '2026-09-10', '08:00:00', 'AGENDADO');
insert into agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa)
values ((select a.id from agendas a join medicos m on m.id = a.medico_id where m.crm = 'CRM-TO 1001' and a.data = '2026-09-10' and a.horario = '08:00:00'),
        (select id from pacientes where cpf = '123.456.789-09'), '2026-09-01 11:20:00', 'FALTOU', true);

-- Um agendamento cancelado (Carlos com o Dr. Eduardo): fica no historico e o horario voltou
-- a ficar livre.
insert into agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa, data_cancelamento, cancelado_por, motivo_cancelamento)
values ((select a.id from agendas a join medicos m on m.id = a.medico_id
         where m.crm = 'CRM-TO 1002' and a.data = cast(current_date + interval '3' day as date) and a.horario = '15:00:00'),
        (select id from pacientes where cpf = '123.456.789-09'), current_timestamp, 'CANCELADO', null, current_timestamp,
        'carlos.lima', 'Imprevisto no trabalho.');
