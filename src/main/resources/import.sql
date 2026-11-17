-- Perfis (sem o prefixo ROLE_). O InitialUsersInitializer so cria os que faltarem.
INSERT INTO perfis (nome) VALUES ('ADMIN');
INSERT INTO perfis (nome) VALUES ('USER');
INSERT INTO perfis (nome) VALUES ('MEDICO');
INSERT INTO perfis (nome) VALUES ('PACIENTE');

-- Contas de acesso dos pacientes e medicos abaixo. A coluna password guarda o hash BCrypt,
-- gerado com o mesmo BCryptPasswordEncoder da aplicacao; a senha em texto nunca vai para o banco.
-- Senha dos pacientes: Paciente123. Senha dos medicos: Medico123. (Apenas para desenvolvimento.)
INSERT INTO usuarios (login, password, ativo) VALUES ('ana.souza', '$2a$10$2qLqzcexlu/dh6JHCvC2vesH.RC7zdPT8bMj8EEc49yTZy/.cmY1W', TRUE);
INSERT INTO usuarios (login, password, ativo) VALUES ('carlos.lima', '$2a$10$4QRiqPPwBem3KYjhT8k8UO0HvqHvUfGMZAT/wYXX4b2/weA2U4fo2', TRUE);
INSERT INTO usuarios (login, password, ativo) VALUES ('mariana.alves', '$2a$10$yaOZYQPQBDilA74JXRhezuPlTrctYoV21etGiwwjnUizHS/RdT5KW', TRUE);
INSERT INTO usuarios (login, password, ativo) VALUES ('beatriz.rocha', '$2a$10$XT//5CS1NStxv2VSu.062OSSg5IciBP9DnFUhCBZspe3r8oysNGXq', TRUE);
INSERT INTO usuarios (login, password, ativo) VALUES ('eduardo.melo', '$2a$10$q507SAVwbBQYF5RbT91FK.UijHADdlSfgNEOuSFXxOu6LnHaLS/TW', TRUE);

-- Papel de cada conta (tabela de juncao do muitos-para-muitos), buscando os ids pelo nome.
INSERT INTO usuario_perfil (usuario_id, perfil_id) SELECT u.id, p.id FROM usuarios u, perfis p WHERE p.nome = 'PACIENTE' AND u.login IN ('ana.souza', 'carlos.lima', 'mariana.alves');
INSERT INTO usuario_perfil (usuario_id, perfil_id) SELECT u.id, p.id FROM usuarios u, perfis p WHERE p.nome = 'MEDICO' AND u.login IN ('beatriz.rocha', 'eduardo.melo');

-- Cada paciente e cada medico aponta para a sua conta em usuario_id.
INSERT INTO pacientes (nome, cpf, data_nascimento, telefone, email, usuario_id) VALUES ('Ana Souza', '529.982.247-25', '1992-05-14', '(63) 99999-1001', 'ana@email.com', (SELECT id FROM usuarios WHERE login = 'ana.souza'));
INSERT INTO pacientes (nome, cpf, data_nascimento, telefone, email, usuario_id) VALUES ('Carlos Lima', '123.456.789-09', '1985-09-22', '(63) 99999-1002', 'carlos@email.com', (SELECT id FROM usuarios WHERE login = 'carlos.lima'));
INSERT INTO pacientes (nome, cpf, data_nascimento, telefone, email, usuario_id) VALUES ('Mariana Alves', '987.654.321-00', '2000-01-10', '(63) 99999-1003', 'mariana@email.com', (SELECT id FROM usuarios WHERE login = 'mariana.alves'));

INSERT INTO medicos (nome, crm, especialidade, telefone, email, usuario_id) VALUES ('Dra. Beatriz Rocha', 'CRM-TO 1001', 'Cardiologia', '(63) 99999-2001', 'beatriz@clinica.com', (SELECT id FROM usuarios WHERE login = 'beatriz.rocha'));
INSERT INTO medicos (nome, crm, especialidade, telefone, email, usuario_id) VALUES ('Dr. Eduardo Melo', 'CRM-TO 1002', 'Clinica Geral', '(63) 99999-2002', 'eduardo@clinica.com', (SELECT id FROM usuarios WHERE login = 'eduardo.melo'));

-- Historico: tres atendimentos ja realizados. Cada um tem o horario na agenda do medico (AGENDADO),
-- o agendamento do paciente (REALIZADO) e a consulta registrada na confirmacao de presenca.
-- Medicos: 1 = Dra. Beatriz, 2 = Dr. Eduardo. Pacientes: 1 = Ana, 2 = Carlos, 3 = Mariana.
INSERT INTO agendas (medico_id, data, horario, status) VALUES (2, '2026-08-25', '09:00:00', 'AGENDADO');
INSERT INTO agendas (medico_id, data, horario, status) VALUES (1, '2026-09-03', '14:30:00', 'AGENDADO');
INSERT INTO agendas (medico_id, data, horario, status) VALUES (2, '2026-09-05', '10:00:00', 'AGENDADO');

INSERT INTO agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa) VALUES (1, 1, '2026-08-18 10:15:00', 'REALIZADO', TRUE);
INSERT INTO agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa) VALUES (2, 1, '2026-08-27 16:40:00', 'REALIZADO', TRUE);
INSERT INTO agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa) VALUES (3, 2, '2026-08-29 09:05:00', 'REALIZADO', TRUE);

INSERT INTO consultas (agendamento_id, data_realizacao, observacoes) VALUES (1, '2026-08-25 09:00:00', 'Consulta de rotina.');
INSERT INTO consultas (agendamento_id, data_realizacao, observacoes) VALUES (2, '2026-09-03 14:30:00', 'Retorno para avaliacao de exames.');
INSERT INTO consultas (agendamento_id, data_realizacao, observacoes) VALUES (3, '2026-09-05 10:00:00', 'Primeira consulta.');

-- Agenda futura, com datas relativas ao dia em que a aplicacao sobe, para que sempre haja horarios livres.
INSERT INTO agendas (medico_id, data, horario, status) VALUES (1, DATEADD(DAY, 1, CURRENT_DATE), '08:00:00', 'DISPONIVEL');
INSERT INTO agendas (medico_id, data, horario, status) VALUES (1, DATEADD(DAY, 1, CURRENT_DATE), '09:00:00', 'DISPONIVEL');
INSERT INTO agendas (medico_id, data, horario, status) VALUES (1, DATEADD(DAY, 2, CURRENT_DATE), '14:00:00', 'DISPONIVEL');
INSERT INTO agendas (medico_id, data, horario, status) VALUES (2, DATEADD(DAY, 1, CURRENT_DATE), '10:30:00', 'DISPONIVEL');
INSERT INTO agendas (medico_id, data, horario, status) VALUES (2, DATEADD(DAY, 3, CURRENT_DATE), '15:00:00', 'DISPONIVEL');
-- Um horario ja reservado (Mariana com a Dra. Beatriz): aguarda a confirmacao de presenca, sem consulta.
INSERT INTO agendas (medico_id, data, horario, status) VALUES (1, DATEADD(DAY, 2, CURRENT_DATE), '10:00:00', 'AGENDADO');
INSERT INTO agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa) VALUES ((SELECT id FROM agendas WHERE medico_id = 1 AND data = DATEADD(DAY, 2, CURRENT_DATE) AND horario = '10:00:00'), 3, CURRENT_TIMESTAMP, 'AGENDADO', TRUE);
-- Um horario de ontem ainda AGENDADO (Ana com o Dr. Eduardo): a recepcao pode confirmar a presenca
-- ou registrar a falta, que so valem a partir do horario da consulta.
INSERT INTO agendas (medico_id, data, horario, status) VALUES (2, DATEADD(DAY, -1, CURRENT_DATE), '16:00:00', 'AGENDADO');
INSERT INTO agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa) VALUES ((SELECT id FROM agendas WHERE medico_id = 2 AND data = DATEADD(DAY, -1, CURRENT_DATE) AND horario = '16:00:00'), 1, DATEADD(DAY, -3, CURRENT_TIMESTAMP), 'AGENDADO', TRUE);
-- Uma falta no historico (Carlos com a Dra. Beatriz): o horario segue ocupado e nao ha consulta.
INSERT INTO agendas (medico_id, data, horario, status) VALUES (1, '2026-09-10', '08:00:00', 'AGENDADO');
INSERT INTO agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa) VALUES ((SELECT id FROM agendas WHERE medico_id = 1 AND data = '2026-09-10' AND horario = '08:00:00'), 2, '2026-09-01 11:20:00', 'FALTOU', TRUE);
-- Um agendamento cancelado (Carlos com o Dr. Eduardo): fica no historico e o horario voltou a ficar livre.
INSERT INTO agendamentos (agenda_id, paciente_id, data_criacao, status, reserva_ativa) VALUES ((SELECT id FROM agendas WHERE medico_id = 2 AND data = DATEADD(DAY, 3, CURRENT_DATE) AND horario = '15:00:00'), 2, CURRENT_TIMESTAMP, 'CANCELADO', NULL);
