package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    // Paciente dono da conta de acesso com este login.
    Optional<Paciente> findByUsuarioLogin(String login);

    /**
     * Busca pelo nome (parte dele, sem diferenciar maiúsculas) ou pelos dígitos do CPF
     * (com ou sem pontuação). Os padrões já chegam prontos para o like, com % e _ escapados;
     * "cpf" é null quando o texto buscado não tem dígitos.
     */
    @Query("""
            select p from Paciente p
            where lower(p.nome) like :nome escape '!'
               or (:cpf is not null and replace(replace(p.cpf, '.', ''), '-', '') like :cpf)
            """)
    Page<Paciente> buscar(@Param("nome") String nome, @Param("cpf") String cpf, Pageable pageable);
}
