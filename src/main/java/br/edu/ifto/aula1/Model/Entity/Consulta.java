package br.edu.ifto.aula1.Model.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * O atendimento efetivamente realizado. Só é criada quando a clínica confirma a presença
 * do paciente (AgendamentoService.confirmarPresenca), nunca no agendamento.
 *
 * Paciente e médico não são colunas daqui: vêm do agendamento e da agenda dele.
 * A restrição única garante no máximo uma consulta por agendamento.
 */
@Entity
@Table(name = "consultas", uniqueConstraints = @UniqueConstraint(
        name = "uk_consulta_agendamento", columnNames = "agendamento_id"))
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false, updatable = false)
    private Agendamento agendamento;

    @Column(name = "data_realizacao", nullable = false, updatable = false)
    private LocalDateTime dataRealizacao;

    @Column(length = 500)
    private String observacoes;

    protected Consulta() {
    }

    public Consulta(Agendamento agendamento, LocalDateTime dataRealizacao) {
        this.agendamento = agendamento;
        this.dataRealizacao = dataRealizacao;
    }

    public Long getId() {
        return id;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }

    public Paciente getPaciente() {
        return agendamento.getPaciente();
    }

    public Medico getMedico() {
        return agendamento.getAgenda().getMedico();
    }

    public LocalDateTime getDataRealizacao() {
        return dataRealizacao;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }
}
