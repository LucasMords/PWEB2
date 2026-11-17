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

import java.time.LocalDateTime;

/**
 * A reserva de um horário da agenda por um paciente.
 *
 * Um mesmo horário pode ter vários agendamentos ao longo do tempo (os cancelados ficam
 * como histórico), mas só um ativo. O H2 não tem índice único parcial (unique ... where
 * status diferente de CANCELADO), então a regra é garantida pela coluna reserva_ativa:
 * ela vale TRUE enquanto o agendamento ocupa o horário e NULL depois de cancelado. Como o
 * banco não considera dois NULL iguais, a restrição única (agenda_id, reserva_ativa)
 * aceita quantos cancelados houver e no máximo um ativo por horário.
 */
@Entity
@Table(name = "agendamentos", uniqueConstraints = @UniqueConstraint(
        name = "uk_agendamento_reserva_ativa", columnNames = {"agenda_id", "reserva_ativa"}))
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_id", nullable = false, updatable = false)
    private Agenda agenda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false, updatable = false)
    private Paciente paciente;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAgendamento status = StatusAgendamento.AGENDADO;

    // TRUE enquanto ocupa o horário (AGENDADO ou REALIZADO); NULL quando CANCELADO.
    @Column(name = "reserva_ativa")
    private Boolean reservaAtiva = Boolean.TRUE;

    protected Agendamento() {
    }

    /** Todo agendamento nasce AGENDADO. */
    public Agendamento(Agenda agenda, Paciente paciente, LocalDateTime dataCriacao) {
        this.agenda = agenda;
        this.paciente = paciente;
        this.dataCriacao = dataCriacao;
    }

    public boolean isAgendado() {
        return status == StatusAgendamento.AGENDADO;
    }

    // As transições abaixo são chamadas pelo AgendamentoService, que confere antes
    // se o agendamento está AGENDADO.

    public void cancelar() {
        this.status = StatusAgendamento.CANCELADO;
        this.reservaAtiva = null;
    }

    public void marcarRealizado() {
        this.status = StatusAgendamento.REALIZADO;
    }

    public Long getId() {
        return id;
    }

    public Agenda getAgenda() {
        return agenda;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public StatusAgendamento getStatus() {
        return status;
    }
}
