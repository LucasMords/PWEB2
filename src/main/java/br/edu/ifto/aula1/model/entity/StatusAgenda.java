package br.edu.ifto.aula1.model.entity;

/** Situação de um horário da agenda do médico. */
public enum StatusAgenda {
    DISPONIVEL("Disponível"),
    AGENDADO("Agendado");

    private final String descricao;

    StatusAgenda(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
