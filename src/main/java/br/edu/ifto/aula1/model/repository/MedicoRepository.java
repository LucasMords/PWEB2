package br.edu.ifto.aula1.model.repository;

import br.edu.ifto.aula1.model.entity.Medico;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    /** Lê e bloqueia o médico até o fim da transação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medico m where m.id = :id")
    Optional<Medico> buscarComBloqueio(@Param("id") Long id);

    boolean existsByCrm(String crm);

    boolean existsByCrmAndIdNot(String crm, Long id);

    // Médico dono da conta de acesso com este login.
    Optional<Medico> findByUsuarioLogin(String login);

    // Especialidades existentes, para o filtro da agenda.
    @Query("select distinct m.especialidade from Medico m order by m.especialidade")
    List<String> buscarEspecialidades();
}
