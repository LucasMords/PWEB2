package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.Agenda;
import br.edu.ifto.aula1.Model.Entity.StatusAgenda;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AgendaRepository extends JpaRepository<Agenda, Long> {

    boolean existsByMedicoIdAndDataAndHorario(Long medicoId, LocalDate data, LocalTime horario);

    boolean existsByMedicoId(Long medicoId);

    /** Horários já cadastrados do médico no período, para a geração em lote pular os repetidos. */
    List<Agenda> findByMedicoIdAndDataBetween(Long medicoId, LocalDate inicio, LocalDate fim);

    /** Todos os horários do dia (livres ou não), com o médico, para a agenda do dia. */
    @Query("""
            select a from Agenda a join fetch a.medico m
            where a.data = :data
              and (:medicoId is null or m.id = :medicoId)
            order by m.nome, m.id, a.horario
            """)
    List<Agenda> buscarDoDia(@Param("data") LocalDate data, @Param("medicoId") Long medicoId);

    // O médico é LAZY e o open-in-view está desligado: a tela de confirmação precisa dele.
    @EntityGraph(attributePaths = "medico")
    Optional<Agenda> findComMedicoById(Long id);

    /**
     * Lê o horário com bloqueio pessimista (SELECT ... FOR UPDATE). Dentro de uma transação,
     * quem chega depois espera o commit de quem chegou primeiro e só então lê o status,
     * já atualizado. É o que impede dois pacientes de reservarem o mesmo horário.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Agenda a where a.id = :id")
    Optional<Agenda> buscarComBloqueio(@Param("id") Long id);

    /**
     * Horários com o status informado que ainda não passaram, com filtros opcionais
     * (parâmetro null = sem filtro). A paginação e os filtros são resolvidos no banco.
     */
    @Query(value = """
            select a from Agenda a join fetch a.medico m
            where a.status = :status
              and (a.data > :hoje or (a.data = :hoje and a.horario > :agora))
              and (:medicoId is null or m.id = :medicoId)
              and (:data is null or a.data = :data)
              and (:especialidade is null or m.especialidade = :especialidade)
            order by a.data, a.horario, m.nome, a.id
            """,
            countQuery = """
            select count(a) from Agenda a join a.medico m
            where a.status = :status
              and (a.data > :hoje or (a.data = :hoje and a.horario > :agora))
              and (:medicoId is null or m.id = :medicoId)
              and (:data is null or a.data = :data)
              and (:especialidade is null or m.especialidade = :especialidade)
            """)
    Page<Agenda> buscarFuturos(@Param("status") StatusAgenda status,
                               @Param("hoje") LocalDate hoje,
                               @Param("agora") LocalTime agora,
                               @Param("medicoId") Long medicoId,
                               @Param("data") LocalDate data,
                               @Param("especialidade") String especialidade,
                               Pageable pageable);
}
