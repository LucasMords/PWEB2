package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    @Override
    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findAll();

    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findByPacienteIdOrderByDataHoraDesc(Long pacienteId);

    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findByMedicoIdOrderByDataHoraDesc(Long medicoId);
}
