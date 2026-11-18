package br.edu.ifto.aula1.Dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Formulário de geração de horários em lote: "de tal data a tal data, nestes dias da
 * semana, das 8h às 12h, a cada 30 minutos". As regras que envolvem mais de um campo
 * (período, intervalo do dia) são conferidas no AgendaService.
 */
public class AgendaLoteForm {

    // Sem @NotNull pelo mesmo motivo do AgendaForm: o id vem da conta do médico autenticado.
    private Long medicoId;

    @NotNull(message = "Informe a data inicial.")
    @FutureOrPresent(message = "A data inicial não pode estar no passado.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataInicio;

    @NotNull(message = "Informe a data final.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataFim;

    @NotEmpty(message = "Selecione ao menos um dia da semana.")
    private Set<DayOfWeek> diasSemana = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    @NotNull(message = "Informe o horário inicial.")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaInicio = LocalTime.of(8, 0);

    @NotNull(message = "Informe o horário final.")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaFim = LocalTime.of(12, 0);

    @NotNull(message = "Informe a duração de cada consulta.")
    @Min(value = 10, message = "A duração mínima é de 10 minutos.")
    @Max(value = 240, message = "A duração máxima é de 240 minutos.")
    private Integer duracaoMinutos = 30;

    public Long getMedicoId() {
        return medicoId;
    }

    public void setMedicoId(Long medicoId) {
        this.medicoId = medicoId;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }

    public Set<DayOfWeek> getDiasSemana() {
        return diasSemana;
    }

    public void setDiasSemana(Set<DayOfWeek> diasSemana) {
        this.diasSemana = diasSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFim() {
        return horaFim;
    }

    public void setHoraFim(LocalTime horaFim) {
        this.horaFim = horaFim;
    }

    public Integer getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public void setDuracaoMinutos(Integer duracaoMinutos) {
        this.duracaoMinutos = duracaoMinutos;
    }
}
