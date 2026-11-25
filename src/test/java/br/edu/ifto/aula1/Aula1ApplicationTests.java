package br.edu.ifto.aula1;

import br.edu.ifto.aula1.model.entity.Consulta;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.model.entity.StatusAgendamento;
import br.edu.ifto.aula1.model.repository.AgendaRepository;
import br.edu.ifto.aula1.model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.model.repository.ConsultaRepository;
import br.edu.ifto.aula1.model.repository.MedicoRepository;
import br.edu.ifto.aula1.model.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Aula1ApplicationTests {
    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private MedicoRepository medicos;

    @Autowired
    private AgendaRepository agendas;

    @Autowired
    private AgendamentoRepository agendamentos;

    @Autowired
    private ConsultaRepository consultas;

    @Test
    void contextLoadsEImportaDadosIniciais() {
        assertThat(pacientes.count()).isGreaterThanOrEqualTo(3);
        assertThat(medicos.count()).isGreaterThanOrEqualTo(2);
        assertThat(agendas.count()).isGreaterThanOrEqualTo(9);
        assertThat(agendamentos.count()).isGreaterThanOrEqualTo(5);
        assertThat(consultas.count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void consultaChegaAoPacienteEAoMedicoPeloAgendamento() {
        Paciente ana = pacientes.findByUsuarioLogin("ana.souza").orElseThrow();
        Medico eduardo = medicos.findByUsuarioLogin("eduardo.melo").orElseThrow();

        // Consulta dos dados de demonstração: Ana com o Dr. Eduardo. O @EntityGraph traz o caminho inteiro,
        // então os nomes podem ser lidos fora de uma transação.
        Consulta consulta = consultas.findByAgendamentoPacienteIdOrderByDataRealizacaoDesc(ana.getId()).stream()
                .filter(c -> "Consulta de rotina.".equals(c.getObservacoes()))
                .findFirst().orElseThrow();

        assertThat(consulta.getPaciente().getNome()).isEqualTo("Ana Souza");
        assertThat(consulta.getMedico().getNome()).isEqualTo("Dr. Eduardo Melo");
        assertThat(consulta.getAgendamento().getStatus()).isEqualTo(StatusAgendamento.REALIZADO);
        assertThat(consultas.findByAgendamentoAgendaMedicoIdOrderByDataRealizacaoDesc(eduardo.getId()))
                .extracting(Consulta::getId).contains(consulta.getId());
    }
}
