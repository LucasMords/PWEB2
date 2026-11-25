package br.edu.ifto.aula1.model.repository;

import br.edu.ifto.aula1.model.entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLogin(String login);

    // Já traz os perfis na mesma consulta, para uso fora de uma transação (ex.: no template).
    @EntityGraph(attributePaths = "perfis")
    Optional<Usuario> findComPerfisByLogin(String login);

    boolean existsByLogin(String login);

    /** Com bloqueio pessimista: duas senhas erradas ao mesmo tempo não se perdem na contagem. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.login = :login")
    Optional<Usuario> buscarComBloqueio(@Param("login") String login);
}
