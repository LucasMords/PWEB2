package br.edu.ifto.aula1.security;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import br.edu.ifto.aula1.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Bloqueio temporário da conta depois de várias senhas erradas seguidas, contra tentativa
 * e erro. Enquanto bloqueada, a conta não entra nem com a senha certa (o Spring Security
 * confere o bloqueio antes da senha). O bloqueio termina sozinho após {@link #DURACAO_BLOQUEIO}.
 */
@Service
public class TentativasLoginService {

    public static final int LIMITE_TENTATIVAS = 5;
    public static final Duration DURACAO_BLOQUEIO = Duration.ofMinutes(15);

    /** O login digitado vai para o registro; limitado para não gravar texto arbitrário longo. */
    private static final int TAMANHO_MAXIMO_LOGIN = 80;

    private static final Logger log = LoggerFactory.getLogger(TentativasLoginService.class);

    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public TentativasLoginService(UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock,
                                  PlatformTransactionManager transacoes) {
        this.transacao = new TransactionTemplate(transacoes);
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    /** Uma senha errada já contada, a ser auditada; bloqueadoAte só vem quando ela bloqueou a conta. */
    public record Falha(String login, boolean contaExiste, LocalDateTime bloqueadoAte) {
    }

    /**
     * Conta a falha e depois a audita. Fora de qualquer transação (NEVER): a auditoria usa outra
     * conexão, e pedi-la com a conta ainda bloqueada esgotaria o pool quando há muitas falhas juntas.
     */
    @Transactional(propagation = Propagation.NEVER)
    public void registrarFalha(String loginDigitado) {
        auditarFalha(transacao.execute(status -> contarFalha(loginDigitado)));
    }

    /** Só a contagem, na transação de quem chama, que deve chamar {@link #auditarFalha} depois dela. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Falha contarFalha(String loginDigitado) {
        String login = limitar(loginDigitado);
        return usuarios.buscarComBloqueio(login).map(usuario -> {
            boolean bloqueou = usuario.registrarFalha(LocalDateTime.now(clock), LIMITE_TENTATIVAS, DURACAO_BLOQUEIO);
            return new Falha(login, true, bloqueou ? usuario.getBloqueadoAte() : null);
        }).orElseGet(() -> new Falha(login, false, null));
    }

    /** Auditoria de uma falha já contada, sem transação aberta. */
    @Transactional(propagation = Propagation.NEVER)
    public void auditarFalha(Falha falha) {
        auditoria.registrarSeparado(falha.login(), AcaoAuditoria.LOGIN_FALHOU,
                falha.contaExiste() ? null : "Usuário inexistente");
        if (falha.bloqueadoAte() != null) {
            log.warn("Conta {} bloqueada até {} por excesso de tentativas", falha.login(), falha.bloqueadoAte());
            auditoria.registrarSeparado(falha.login(), AcaoAuditoria.CONTA_BLOQUEADA,
                    "Bloqueada até " + falha.bloqueadoAte().withNano(0));
        }
    }

    @Transactional(readOnly = true)
    public boolean bloqueada(String login) {
        LocalDateTime agora = LocalDateTime.now(clock);
        return usuarios.findByLogin(login).map(usuario -> usuario.isBloqueadoEm(agora)).orElse(false);
    }

    /** Chamado a cada autenticação bem-sucedida (inclusive HTTP Basic): só grava se havia falhas. */
    @Transactional
    public void registrarSucesso(String login) {
        usuarios.findByLogin(login).ifPresent(usuario -> usuario.registrarSucesso());
    }

    private static String limitar(String login) {
        if (login == null || login.isBlank())
            return "(vazio)";
        return login.length() <= TAMANHO_MAXIMO_LOGIN ? login : login.substring(0, TAMANHO_MAXIMO_LOGIN);
    }
}
