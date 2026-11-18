package br.edu.ifto.aula1.Security;

import br.edu.ifto.aula1.Model.Entity.AcaoAuditoria;
import br.edu.ifto.aula1.Model.repository.UsuarioRepository;
import br.edu.ifto.aula1.Service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public TentativasLoginService(UsuarioRepository usuarios, AuditoriaService auditoria, Clock clock) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public void registrarFalha(String loginDigitado) {
        String login = limitar(loginDigitado);
        LocalDateTime agora = LocalDateTime.now(clock);

        usuarios.buscarComBloqueio(login).ifPresentOrElse(usuario -> {
            boolean bloqueou = usuario.registrarFalha(agora, LIMITE_TENTATIVAS, DURACAO_BLOQUEIO);
            auditoria.registrarSeparado(login, AcaoAuditoria.LOGIN_FALHOU, null);
            if (bloqueou) {
                log.warn("Conta {} bloqueada até {} por excesso de tentativas", login, usuario.getBloqueadoAte());
                auditoria.registrarSeparado(login, AcaoAuditoria.CONTA_BLOQUEADA,
                        "Bloqueada até " + usuario.getBloqueadoAte().withNano(0));
            }
        }, () -> auditoria.registrarSeparado(login, AcaoAuditoria.LOGIN_FALHOU, "Usuário inexistente"));
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
