package br.edu.ifto.aula1.Model.Entity;

import jakarta.persistence.Column;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import jakarta.validation.groups.ConvertGroup;
import br.edu.ifto.aula1.Validation.Cadastro;
import br.edu.ifto.aula1.Validation.Edicao;
import br.edu.ifto.aula1.Validation.Referencia;
import jakarta.validation.constraints.*;
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
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "consultas")
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Null(groups = Cadastro.class, message = "{registro.id.cadastro}")
    @NotNull(groups = {Edicao.class, Referencia.class}, message = "{registro.id.obrigatorio}")
    @Positive(groups = {Edicao.class, Referencia.class}, message = "{registro.id.positivo}")
    private Long id;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @Column(name = "data_hora", nullable = false)
    @NotNull(message = "{consulta.dataHora.obrigatoria}")
    private LocalDateTime dataHora;

    @Column(length = 500)
    @Size(max = 500, message = "{texto.tamanho}")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull(message = "{consulta.status.obrigatorio}")
    private StatusConsulta status = StatusConsulta.AGENDADA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    @NotNull(message = "{consulta.paciente.obrigatorio}")
    @Valid
    @ConvertGroup.List({
        @ConvertGroup(from = Default.class, to = Referencia.class),
        @ConvertGroup(from = Cadastro.class, to = Referencia.class),
        @ConvertGroup(from = Edicao.class, to = Referencia.class)
    })
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    @NotNull(message = "{consulta.medico.obrigatorio}")
    @Valid
    @ConvertGroup.List({
        @ConvertGroup(from = Default.class, to = Referencia.class),
        @ConvertGroup(from = Cadastro.class, to = Referencia.class),
        @ConvertGroup(from = Edicao.class, to = Referencia.class)
    })
    private Medico medico;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public StatusConsulta getStatus() {
        return status;
    }

    public void setStatus(StatusConsulta status) {
        this.status = status;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = paciente;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }
}
