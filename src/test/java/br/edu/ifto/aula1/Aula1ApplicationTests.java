package br.edu.ifto.aula1;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.StatusConsulta;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
import br.edu.ifto.aula1.Model.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Aula1ApplicationTests {
    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private MedicoRepository medicos;

    @Autowired
    private ConsultaRepository consultas;

    @Test
    void contextLoadsEImportaDadosIniciais() {
        assertThat(pacientes.count()).isGreaterThanOrEqualTo(3);
        assertThat(medicos.count()).isGreaterThanOrEqualTo(2);
        assertThat(consultas.count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void consultaDeveEstarAssociadaAoPacienteEAoMedico() {
        Paciente paciente = pacientes.findAll().getFirst();
        Medico medico = medicos.findAll().getFirst();
        Consulta consulta = new Consulta();
        consulta.setDataHora(LocalDateTime.of(2026, 10, 1, 8, 30));
        consulta.setStatus(StatusConsulta.AGENDADA);
        consulta.setPaciente(paciente);
        consulta.setMedico(medico);
        Consulta salva = consultas.saveAndFlush(consulta);
        assertThat(consultas.findByPacienteIdOrderByDataHoraDesc(paciente.getId()))
                .extracting(Consulta::getId).contains(salva.getId());
        assertThat(consultas.findByMedicoIdOrderByDataHoraDesc(medico.getId()))
                .extracting(Consulta::getId).contains(salva.getId());
    }
}
