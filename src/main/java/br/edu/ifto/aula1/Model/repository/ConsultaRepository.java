package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    // paciente e medico são LAZY e o open-in-view está desligado: o @EntityGraph os carrega
    // na mesma consulta para que a listagem consiga exibir os nomes.

    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findAllByOrderByDataHoraDesc();

    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findByPacienteIdOrderByDataHoraDesc(Long pacienteId);

    @EntityGraph(attributePaths = {"paciente", "medico"})
    List<Consulta> findByMedicoIdOrderByDataHoraDesc(Long medicoId);
}
