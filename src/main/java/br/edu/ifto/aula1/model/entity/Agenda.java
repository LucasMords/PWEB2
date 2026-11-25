package br.edu.ifto.aula1.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Um horário disponibilizado por um médico. A restrição única impede que o mesmo médico
 * tenha dois registros para a mesma data e horário, esteja o horário livre ou reservado.
 */
@Entity
@Table(name = "agendas", uniqueConstraints = @UniqueConstraint(
        name = "uk_agenda_medico_data_horario", columnNames = {"medico_id", "data", "horario"}))
public class Agenda {

    public static final int DURACAO_PADRAO_MINUTOS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false, updatable = false)
    private Medico medico;

    @Column(nullable = false, updatable = false)
    private LocalDate data;

    @Column(nullable = false, updatable = false)
    private LocalTime horario;

    @Column(name = "duracao_minutos", nullable = false, updatable = false)
    private int duracaoMinutos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgenda status = StatusAgenda.DISPONIVEL;

    protected Agenda() {
    }

    /** Todo horário nasce DISPONIVEL. Sem duração informada, vale a padrão. */
    public Agenda(Medico medico, LocalDate data, LocalTime horario) {
        this(medico, data, horario, DURACAO_PADRAO_MINUTOS);
    }

    public Agenda(Medico medico, LocalDate data, LocalTime horario, int duracaoMinutos) {
        this.medico = medico;
        this.data = data;
        this.horario = horario;
        this.duracaoMinutos = duracaoMinutos;
    }

    /**
     * Os dois horários ocupam algum minuto em comum? Um que começa exatamente quando o outro
     * termina não se sobrepõe. Compara minutos do dia para não "virar" a meia-noite.
     */
    public boolean sobrepoe(Agenda outra) {
        return data.equals(outra.getData())
                && minutoInicial() < outra.minutoInicial() + outra.getDuracaoMinutos()
                && outra.minutoInicial() < minutoInicial() + duracaoMinutos;
    }

    /** O atendimento termina no mesmo dia em que começa (até a meia-noite)? */
    public boolean terminaNoMesmoDia() {
        return minutoInicial() + duracaoMinutos <= 24 * 60;
    }

    private int minutoInicial() {
        return getHorario().getHour() * 60 + getHorario().getMinute();
    }

    public boolean isDisponivel() {
        return status == StatusAgenda.DISPONIVEL;
    }

    /** Chamado pelo AgendamentoService ao criar um agendamento para este horário. */
    public void reservar() {
        this.status = StatusAgenda.AGENDADO;
    }

    /** Chamado pelo AgendamentoService ao cancelar o agendamento deste horário. */
    public void liberar() {
        this.status = StatusAgenda.DISPONIVEL;
    }

    public LocalDateTime getDataHora() {
        return LocalDateTime.of(data, horario);
    }

    public Long getId() {
        return id;
    }

    public Medico getMedico() {
        return medico;
    }

    public LocalDate getData() {
        return data;
    }

    public LocalTime getHorario() {
        return horario;
    }

    public int getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public StatusAgenda getStatus() {
        return status;
    }
}
