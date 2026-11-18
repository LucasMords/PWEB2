package br.edu.ifto.aula1.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filtros da tela de horários disponíveis. Campo null = sem filtro. */
public class AgendaFiltro {

    private Long medicoId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate data;

    private String especialidade;

    public Long getMedicoId() {
        return medicoId;
    }

    public void setMedicoId(Long medicoId) {
        this.medicoId = medicoId;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    // A opção "Todas" do formulário envia texto vazio, que aqui vira null (sem filtro).
    public void setEspecialidade(String especialidade) {
        this.especialidade = (especialidade == null || especialidade.isBlank()) ? null : especialidade.trim();
    }
}
