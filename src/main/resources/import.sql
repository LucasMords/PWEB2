INSERT INTO pacientes (nome, cpf, data_nascimento, telefone, email) VALUES ('Ana Souza', '111.111.111-11', '1992-05-14', '(63) 99999-1001', 'ana@email.com');
INSERT INTO pacientes (nome, cpf, data_nascimento, telefone, email) VALUES ('Carlos Lima', '222.222.222-22', '1985-09-22', '(63) 99999-1002', 'carlos@email.com');
INSERT INTO pacientes (nome, cpf, data_nascimento, telefone, email) VALUES ('Mariana Alves', '333.333.333-33', '2000-01-10', '(63) 99999-1003', 'mariana@email.com');

INSERT INTO medicos (nome, crm, especialidade, telefone, email) VALUES ('Dra. Beatriz Rocha', 'CRM-TO 1001', 'Cardiologia', '(63) 99999-2001', 'beatriz@clinica.com');
INSERT INTO medicos (nome, crm, especialidade, telefone, email) VALUES ('Dr. Eduardo Melo', 'CRM-TO 1002', 'Clinica Geral', '(63) 99999-2002', 'eduardo@clinica.com');

INSERT INTO consultas (data_hora, observacoes, status, paciente_id, medico_id) VALUES ('2026-08-25 09:00:00', 'Consulta de rotina.', 'REALIZADA', 1, 2);
INSERT INTO consultas (data_hora, observacoes, status, paciente_id, medico_id) VALUES ('2026-09-03 14:30:00', 'Retorno para avaliacao de exames.', 'AGENDADA', 1, 1);
INSERT INTO consultas (data_hora, observacoes, status, paciente_id, medico_id) VALUES ('2026-09-05 10:00:00', 'Primeira consulta.', 'AGENDADA', 2, 2);
