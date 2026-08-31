package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {
}
