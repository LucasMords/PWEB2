package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Medico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    boolean existsByCrm(String crm);

    boolean existsByCrmAndIdNot(String crm, Long id);

    // Médico dono da conta de acesso com este login.
    Optional<Medico> findByUsuarioLogin(String login);

    // Especialidades existentes, para o filtro da agenda.
    @Query("select distinct m.especialidade from Medico m order by m.especialidade")
    List<String> buscarEspecialidades();
}
