package br.edu.ifto.aula1.dto;

import jakarta.validation.constraints.Size;

/**
 * Único dado editável de uma consulta registrada: as observações. Paciente, médico e
 * data de realização vêm da confirmação de presença e não mudam.
 */
public class ConsultaForm {

    private Long id;

    @Size(max = 500, message = "As observações devem ter no máximo {max} caracteres.")
    private String observacoes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }
}
