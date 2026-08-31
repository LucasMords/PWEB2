package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Medico;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicoRepository extends JpaRepository<Medico, Long> {
}
