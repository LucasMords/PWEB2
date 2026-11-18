package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Model.Entity.AcaoAuditoria;
import br.edu.ifto.aula1.Model.Entity.RegistroAuditoria;
import br.edu.ifto.aula1.Model.repository.RegistroAuditoriaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro de auditoria: quem fez o quê, quando e em qual registro.
 *
 * {@link #registrar} roda na transação de quem chama: se a operação for desfeita, o
 * registro também é, e a auditoria nunca diz que algo aconteceu sem ter acontecido.
 * {@link #registrarSeparado} é para eventos que não têm transação própria, como o login.
 */
@Service
@Transactional(readOnly = true)
public class AuditoriaService {

    /** Quem aparece quando não há usuário autenticado (ex.: tarefas internas, cadastro público). */
    public static final String SISTEMA = "sistema";

    private final RegistroAuditoriaRepository repository;
    private final Clock clock;

    public AuditoriaService(RegistroAuditoriaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Ação do usuário autenticado, na transação atual. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(AcaoAuditoria acao, String entidade, Long entidadeId, String detalhe) {
        repository.save(new RegistroAuditoria(LocalDateTime.now(clock), usuarioAtual(), acao, entidade, entidadeId, detalhe));
    }

    /** Evento fora de uma operação de negócio (login, bloqueio), gravado na própria transação. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSeparado(String usuario, AcaoAuditoria acao, String detalhe) {
        repository.save(new RegistroAuditoria(LocalDateTime.now(clock), usuario, acao, "Usuario", null, detalhe));
    }

    /** "ate" é inclusivo: o dia inteiro entra no filtro. */
    public Page<RegistroAuditoria> buscar(String usuario, AcaoAuditoria acao, LocalDate de, LocalDate ate, Pageable pageable) {
        return repository.buscar(usuario == null || usuario.isBlank() ? null : usuario.trim(), acao,
                de == null ? null : de.atStartOfDay(),
                ate == null ? null : ate.plusDays(1).atStartOfDay(),
                pageable);
    }

    private static String usuarioAtual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken || autenticacao.getName() == null)
            return SISTEMA;
        return autenticacao.getName();
    }
}
