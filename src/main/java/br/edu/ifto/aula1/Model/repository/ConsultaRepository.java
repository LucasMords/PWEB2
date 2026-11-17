package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    // Paciente e médico vêm pelo agendamento. Tudo é LAZY e o open-in-view está desligado:
    // o @EntityGraph carrega o caminho inteiro na mesma consulta para a tela exibir os nomes.

    @EntityGraph(attributePaths = {"agendamento.paciente", "agendamento.agenda.medico"})
    List<Consulta> findAllByOrderByDataRealizacaoDesc();

    @EntityGraph(attributePaths = {"agendamento.paciente", "agendamento.agenda.medico"})
    List<Consulta> findByAgendamentoPacienteIdOrderByDataRealizacaoDesc(Long pacienteId);

    @EntityGraph(attributePaths = {"agendamento.paciente", "agendamento.agenda.medico"})
    List<Consulta> findByAgendamentoAgendaMedicoIdOrderByDataRealizacaoDesc(Long medicoId);

    @EntityGraph(attributePaths = {"agendamento.paciente", "agendamento.agenda.medico"})
    Optional<Consulta> findDetalhadaById(Long id);

    boolean existsByAgendamentoId(Long agendamentoId);

    Optional<Consulta> findByAgendamentoId(Long agendamentoId);
}
