package br.edu.ifto.aula1.model.repository;

import br.edu.ifto.aula1.model.entity.Agendamento;
import br.edu.ifto.aula1.model.entity.StatusAgendamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
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

public interface AgendamentoRepository extends JpaRepository<Agendamento, Long> {

    boolean existsByPacienteId(Long pacienteId);

    /** Há algum agendamento não cancelado ocupando este horário? */
    boolean existsByAgendaIdAndStatusNot(Long agendaId, StatusAgendamento status);

    /** Todos os agendamentos de um horário, do mais antigo ao mais novo (histórico). */
    List<Agendamento> findByAgendaIdOrderByIdAsc(Long agendaId);

    /** Agendamento com horário, médico e paciente, para telas que mostram um só (ex.: reagendamento). */
    @EntityGraph(attributePaths = {"agenda", "agenda.medico", "paciente"})
    Optional<Agendamento> findDetalhadoById(Long id);

    /** Agendamentos que ocupam horários do dia (todos menos os cancelados), com o paciente. */
    @Query("""
            select ag from Agendamento ag
              join fetch ag.agenda a
              join fetch ag.paciente p
            where a.data = :data
              and ag.status <> :cancelado
              and (:medicoId is null or a.medico.id = :medicoId)
            """)
    List<Agendamento> buscarAtivosDoDia(@Param("data") LocalDate data,
                                        @Param("medicoId") Long medicoId,
                                        @Param("cancelado") StatusAgendamento cancelado);

    /** Agendamentos do paciente com este status a partir de um instante, do mais próximo ao mais distante. */
    @Query("""
            select ag from Agendamento ag
              join fetch ag.agenda a
              join fetch a.medico
            where ag.paciente.id = :pacienteId
              and ag.status = :status
              and (a.data > :data or (a.data = :data and a.horario >= :horario))
            order by a.data, a.horario
            """)
    List<Agendamento> buscarProximosDoPaciente(@Param("pacienteId") Long pacienteId,
                                               @Param("status") StatusAgendamento status,
                                               @Param("data") LocalDate data,
                                               @Param("horario") LocalTime horario,
                                               Limit limite);

    /**
     * Lê o agendamento com bloqueio pessimista, para que cancelamento e confirmação de
     * presença do mesmo agendamento nunca rodem ao mesmo tempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ag from Agendamento ag where ag.id = :id")
    Optional<Agendamento> buscarComBloqueio(@Param("id") Long id);

    /**
     * Agendamentos com filtros opcionais (parâmetro null = sem filtro). Agenda, médico e
     * paciente vêm na mesma consulta (join fetch), o que evita N+1 na listagem.
     */
    @Query(value = """
            select ag from Agendamento ag
              join fetch ag.agenda a
              join fetch a.medico m
              join fetch ag.paciente p
            where (:pacienteId is null or p.id = :pacienteId)
              and (:medicoId is null or m.id = :medicoId)
              and (cast(:data as LocalDate) is null or a.data = :data)
              and (:status is null or ag.status = :status)
            order by a.data desc, a.horario desc, ag.id desc
            """,
            countQuery = """
            select count(ag) from Agendamento ag
              join ag.agenda a
            where (:pacienteId is null or ag.paciente.id = :pacienteId)
              and (:medicoId is null or a.medico.id = :medicoId)
              and (cast(:data as LocalDate) is null or a.data = :data)
              and (:status is null or ag.status = :status)
            """)
    Page<Agendamento> buscar(@Param("pacienteId") Long pacienteId,
                             @Param("medicoId") Long medicoId,
                             @Param("data") LocalDate data,
                             @Param("status") StatusAgendamento status,
                             Pageable pageable);
}
