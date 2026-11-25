package br.edu.ifto.aula1.security;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.service.AuditoriaService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Reage aos eventos de autenticação do Spring Security: conta as senhas erradas, zera a
 * contagem quando a pessoa acerta e registra na auditoria cada entrada pelo formulário de
 * login. (O HTTP Basic autentica a cada requisição; por isso a auditoria de LOGIN usa só o
 * evento "interativo", disparado pelo formulário e pelo remember-me.)
 */
@Component
public class EventosDeAutenticacao {

    private final TentativasLoginService tentativas;
    private final AuditoriaService auditoria;

    public EventosDeAutenticacao(TentativasLoginService tentativas, AuditoriaService auditoria) {
        this.tentativas = tentativas;
        this.auditoria = auditoria;
    }

    @EventListener
    public void senhaErrada(AuthenticationFailureBadCredentialsEvent evento) {
        tentativas.registrarFalha(evento.getAuthentication().getName());
    }

    @EventListener
    public void autenticou(AuthenticationSuccessEvent evento) {
        // O remember-me não confere a senha: não vale como acerto.
        if (evento.getAuthentication() instanceof RememberMeAuthenticationToken)
            return;
        tentativas.registrarSucesso(evento.getAuthentication().getName());
    }

    @EventListener
    public void entrouPeloFormulario(InteractiveAuthenticationSuccessEvent evento) {
        auditoria.registrarSeparado(evento.getAuthentication().getName(), AcaoAuditoria.LOGIN, null);
    }
}
