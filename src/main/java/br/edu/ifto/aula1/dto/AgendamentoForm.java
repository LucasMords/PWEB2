package br.edu.ifto.aula1.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Dados da tela de confirmação do agendamento. O pacienteId só é usado quando quem
 * agenda é a equipe; para o paciente autenticado ele é ignorado e vale o dono da conta.
 */
public class AgendamentoForm {

    @NotNull(message = "Selecione um horário.")
    private Long agendaId;

    private Long pacienteId;

    public Long getAgendaId() {
        return agendaId;
    }

    public void setAgendaId(Long agendaId) {
        this.agendaId = agendaId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }
}
