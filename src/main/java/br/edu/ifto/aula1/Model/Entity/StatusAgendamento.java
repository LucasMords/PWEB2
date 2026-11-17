package br.edu.ifto.aula1.Model.Entity;

/**
 * Situação de um agendamento. As únicas transições válidas são
 * AGENDADO -> REALIZADO (presença confirmada) e AGENDADO -> CANCELADO.
 */
public enum StatusAgendamento {
    AGENDADO("Agendado"),
    REALIZADO("Realizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusAgendamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
