package br.edu.ifto.aula1;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Validation.Cadastro;
import br.edu.ifto.aula1.Validation.Edicao;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GruposValidacaoTests {
    private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private static final Validator validator = factory.getValidator();

    @AfterAll static void fechar() { factory.close(); }

    private Paciente paciente() {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana");
        paciente.setCpf("52998224725");
        paciente.setDataNascimento(LocalDate.of(1990, 1, 1));
        return paciente;
    }

    @Test void cadastroNaoExigeIdMasEdicaoExige() {
        Paciente paciente = paciente();
        assertThat(validator.validate(paciente, Default.class, Cadastro.class)).isEmpty();
        assertThat(validator.validate(paciente, Default.class, Edicao.class))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("id");
        paciente.setId(1L);
        assertThat(validator.validate(paciente, Default.class, Edicao.class)).isEmpty();
        assertThat(validator.validate(paciente, Default.class, Cadastro.class))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("id");
    }

    @Test void regrasComunsContinuamAtivasNosDoisGrupos() {
        Paciente paciente = paciente();
        paciente.setNome(" ");
        assertThat(validator.validate(paciente, Default.class, Cadastro.class))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("nome");
        paciente.setId(1L);
        assertThat(validator.validate(paciente, Default.class, Edicao.class))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("nome");
    }

    @Test void cascataValidaReferenciasSemExigirNovoCadastroDePacienteEMedico() {
        Consulta consulta = new Consulta();
        consulta.setDataHora(LocalDateTime.now().plusDays(1));
        consulta.setPaciente(new Paciente());
        consulta.setMedico(new Medico());
        assertThat(validator.validate(consulta, Default.class, Cadastro.class))
                .extracting(v -> v.getPropertyPath().toString()).containsExactlyInAnyOrder("paciente.id", "medico.id");
        consulta.getPaciente().setId(1L);
        consulta.getMedico().setId(1L);
        assertThat(validator.validate(consulta, Default.class, Cadastro.class)).isEmpty();
        consulta.setId(1L);
        assertThat(validator.validate(consulta, Default.class, Edicao.class)).isEmpty();
    }
}
