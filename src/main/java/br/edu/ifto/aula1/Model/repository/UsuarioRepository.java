package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    // Já traz os perfis na mesma consulta, para uso fora de uma transação (ex.: no template).
    @EntityGraph(attributePaths = "perfis")
    Optional<Usuario> findComPerfisByLogin(String login);

    boolean existsByLogin(String login);
}
