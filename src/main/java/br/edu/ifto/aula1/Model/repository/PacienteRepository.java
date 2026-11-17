package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    // Paciente dono da conta de acesso com este login.
    Optional<Paciente> findByUsuarioLogin(String login);
}
