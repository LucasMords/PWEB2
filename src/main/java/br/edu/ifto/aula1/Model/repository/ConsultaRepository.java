package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {
    boolean existsByPacienteId(Long id);
    boolean existsByMedicoId(Long id);
    boolean existsByPacienteIdAndDataHoraBefore(Long id, java.time.LocalDateTime hora);

    @org.springframework.data.jpa.repository.Query("select c.id from Consulta c where c.medico.id = :id and c.dataHora = :hora and c.status <> br.edu.ifto.aula1.Model.Entity.StatusConsulta.CANCELADA and (:ignorar is null or c.id <> :ignorar)")
    List<Long> conflitoMedico(Long id, java.time.LocalDateTime hora, Long ignorar);

    @org.springframework.data.jpa.repository.Query("select c.id from Consulta c where c.paciente.id = :id and c.dataHora = :hora and c.status <> br.edu.ifto.aula1.Model.Entity.StatusConsulta.CANCELADA and (:ignorar is null or c.id <> :ignorar)")
    List<Long> conflitoPaciente(Long id, java.time.LocalDateTime hora, Long ignorar);

    @Override
    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findAll();

    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findByPacienteIdOrderByDataHoraDesc(Long pacienteId);

    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findByMedicoIdOrderByDataHoraDesc(Long medicoId);
}
