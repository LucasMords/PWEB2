package br.edu.ifto.aula1.dto;

import br.edu.ifto.aula1.model.entity.StatusAgendamento;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filtros da tela de gerenciamento de agendamentos. Campo null = sem filtro. */
public class AgendamentoFiltro {

    private Long pacienteId;

    private Long medicoId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate data;

    private StatusAgendamento status;

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

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

    public StatusAgendamento getStatus() {
        return status;
    }

    public void setStatus(StatusAgendamento status) {
        this.status = status;
    }
}
