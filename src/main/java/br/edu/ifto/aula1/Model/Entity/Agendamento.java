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

import java.time.Duration;
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

    /** Com quanta antecedência ao horário a presença já pode ser confirmada. */
    public static final Duration ANTECEDENCIA_CONFIRMACAO = Duration.ofHours(1);

    /** Tamanho máximo do motivo do cancelamento. */
    public static final int MOTIVO_MAXIMO = 255;

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

    // TRUE enquanto ocupa o horário (AGENDADO, REALIZADO ou FALTOU); NULL quando CANCELADO.
    @Column(name = "reserva_ativa")
    private Boolean reservaAtiva = Boolean.TRUE;

    // Preenchidos só no cancelamento: histórico de quem cancelou, quando e por quê.
    @Column(name = "data_cancelamento")
    private LocalDateTime dataCancelamento;

    @Column(name = "cancelado_por", length = 50)
    private String canceladoPor;

    @Column(name = "motivo_cancelamento", length = MOTIVO_MAXIMO)
    private String motivoCancelamento;

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

    // Regras de horário das ações. Recebem o "agora" para que o serviço e a tela usem o
    // mesmo instante (e para que os testes possam fixá-lo). O AgendamentoService as confere
    // de novo antes de gravar; na tela elas só habilitam ou desabilitam os botões.

    /** O atendimento confirma a presença a partir de 1 hora antes do horário (o paciente chega antes). */
    public boolean permiteConfirmarPresenca(LocalDateTime agora) {
        return isAgendado() && !agora.isBefore(agenda.getDataHora().minus(ANTECEDENCIA_CONFIRMACAO));
    }

    /** A falta só pode ser registrada depois que o horário começou. */
    public boolean permiteRegistrarFalta(LocalDateTime agora) {
        return isAgendado() && !agora.isBefore(agenda.getDataHora());
    }

    // As transições abaixo são chamadas pelo AgendamentoService, que confere antes
    // se o agendamento está AGENDADO.

    /** Guarda quando, por quem (login) e por que foi cancelado; o motivo é opcional. */
    public void cancelar(LocalDateTime quando, String por, String motivo) {
        this.status = StatusAgendamento.CANCELADO;
        this.reservaAtiva = null;
        this.dataCancelamento = quando;
        this.canceladoPor = por;
        this.motivoCancelamento = motivo;
    }

    public void marcarRealizado() {
        this.status = StatusAgendamento.REALIZADO;
    }

    /** O horário continua ocupado (reserva_ativa segue TRUE): a falta não devolve a vaga, que já passou. */
    public void marcarFalta() {
        this.status = StatusAgendamento.FALTOU;
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

    public LocalDateTime getDataCancelamento() {
        return dataCancelamento;
    }

    public String getCanceladoPor() {
        return canceladoPor;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }
}
