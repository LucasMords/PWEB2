package br.edu.ifto.aula1.Dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Dados do formulário de cadastro de horário. É um objeto só do formulário: a entidade
 * Agenda é criada pelo AgendaService, sempre com o status DISPONIVEL.
 */
public class AgendaForm {

    // Sem @NotNull: só o próprio médico cadastra horários, e o id não vem do formulário,
    // e sim da conta autenticada (AgendaController). O serviço confere que ele existe.
    private Long medicoId;

    @NotNull(message = "Informe a data.")
    @FutureOrPresent(message = "A data não pode estar no passado.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate data;

    @NotNull(message = "Informe o horário.")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horario;

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

    public LocalTime getHorario() {
        return horario;
    }

    public void setHorario(LocalTime horario) {
        this.horario = horario;
    }
}
