package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Medico;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    boolean existsByCrm(String crm);

    boolean existsByCrmAndIdNot(String crm, Long id);
}
