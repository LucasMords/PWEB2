-- Duracao de cada horario da agenda, para conferir sobreposicao (do medico e do paciente).
-- Os horarios que ja existiam ficam com 30 minutos, ou menos quando o proximo horario do
-- mesmo medico no dia comeca antes disso (agendas geradas com consultas mais curtas).
alter table agendas add column duracao_minutos integer default 30 not null;
update agendas set duracao_minutos = greatest(1, least(30, coalesce(cast((
    (select min(extract(epoch from proximo.horario))
       from agendas proximo
      where proximo.medico_id = agendas.medico_id
        and proximo.data = agendas.data
        and proximo.horario > agendas.horario)
    - extract(epoch from agendas.horario)) / 60 as integer), 30)));
alter table agendas add constraint ck_agenda_duracao check (duracao_minutos between 1 and 240);

-- Senha provisoria (redefinida pela administracao): precisa ser trocada no proximo acesso.
alter table usuarios add column trocar_senha boolean default false not null;

alter table auditoria drop constraint ck_auditoria_acao;
alter table auditoria add constraint ck_auditoria_acao check (acao in (
    'LOGIN', 'LOGIN_FALHOU', 'CONTA_BLOQUEADA', 'SENHA_ALTERADA', 'SENHA_REDEFINIDA',
    'HORARIO_CADASTRADO', 'HORARIOS_GERADOS',
    'AGENDAMENTO_CRIADO', 'AGENDAMENTO_CANCELADO', 'AGENDAMENTO_REAGENDADO',
    'PRESENCA_CONFIRMADA', 'FALTA_REGISTRADA', 'CONSULTA_ALTERADA',
    'PACIENTE_CADASTRADO', 'PACIENTE_ALTERADO', 'PACIENTE_EXCLUIDO',
    'MEDICO_CADASTRADO', 'MEDICO_ALTERADO', 'MEDICO_EXCLUIDO',
    'DADOS_EXPORTADOS'));
