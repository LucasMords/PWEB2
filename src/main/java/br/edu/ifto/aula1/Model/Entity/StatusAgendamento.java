package br.edu.ifto.aula1.Model.Entity;

/**
 * Situação de um agendamento. As únicas transições válidas partem de AGENDADO:
 * AGENDADO -> REALIZADO (presença confirmada), AGENDADO -> FALTOU (o paciente não
 * compareceu) e AGENDADO -> CANCELADO.
 */
public enum StatusAgendamento {
    AGENDADO("Agendado"),
    REALIZADO("Realizado"),
    FALTOU("Faltou"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusAgendamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
