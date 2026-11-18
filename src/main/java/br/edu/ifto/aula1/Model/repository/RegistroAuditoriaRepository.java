package br.edu.ifto.aula1.Model.repository;

import br.edu.ifto.aula1.Model.Entity.AcaoAuditoria;
import br.edu.ifto.aula1.Model.Entity.RegistroAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /** Registros com filtros opcionais (null = sem filtro), do mais recente para o mais antigo. */
    @Query("""
            select r from RegistroAuditoria r
            where (:usuario is null or r.usuario = :usuario)
              and (:acao is null or r.acao = :acao)
              and (:inicio is null or r.dataHora >= :inicio)
              and (:fim is null or r.dataHora < :fim)
            order by r.dataHora desc, r.id desc
            """)
    Page<RegistroAuditoria> buscar(@Param("usuario") String usuario,
                                   @Param("acao") AcaoAuditoria acao,
                                   @Param("inicio") LocalDateTime inicio,
                                   @Param("fim") LocalDateTime fim,
                                   Pageable pageable);

    List<RegistroAuditoria> findByUsuarioAndAcaoOrderByIdDesc(String usuario, AcaoAuditoria acao);

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByIdAsc(String entidade, Long entidadeId);
}
