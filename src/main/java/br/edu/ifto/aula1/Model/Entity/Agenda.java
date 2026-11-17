package br.edu.ifto.aula1.Model.Entity;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgenda status = StatusAgenda.DISPONIVEL;

    protected Agenda() {
    }

    /** Todo horário nasce DISPONIVEL. */
    public Agenda(Medico medico, LocalDate data, LocalTime horario) {
        this.medico = medico;
        this.data = data;
        this.horario = horario;
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

    public StatusAgenda getStatus() {
        return status;
    }
}
