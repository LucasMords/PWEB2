package br.edu.ifto.aula1.model.entity;

/** O que foi feito, para o registro de auditoria. */
public enum AcaoAuditoria {
    LOGIN("Entrou no sistema"),
    LOGIN_FALHOU("Senha incorreta"),
    CONTA_BLOQUEADA("Conta bloqueada por tentativas"),

    HORARIO_CADASTRADO("Cadastrou horário"),
    HORARIOS_GERADOS("Gerou horários em lote"),

    AGENDAMENTO_CRIADO("Agendou"),
    AGENDAMENTO_CANCELADO("Cancelou agendamento"),
    AGENDAMENTO_REAGENDADO("Reagendou"),
    PRESENCA_CONFIRMADA("Confirmou presença"),
    FALTA_REGISTRADA("Registrou falta"),
    CONSULTA_ALTERADA("Alterou observações da consulta"),

    PACIENTE_CADASTRADO("Cadastrou paciente"),
    PACIENTE_ALTERADO("Alterou paciente"),
    PACIENTE_EXCLUIDO("Excluiu paciente"),
    MEDICO_CADASTRADO("Cadastrou médico"),
    MEDICO_ALTERADO("Alterou médico"),
    MEDICO_EXCLUIDO("Excluiu médico"),

    DADOS_EXPORTADOS("Exportou os próprios dados (LGPD)");

    private final String descricao;

    AcaoAuditoria(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
