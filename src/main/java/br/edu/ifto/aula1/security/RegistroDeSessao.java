package br.edu.ifto.aula1.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

/**
 * Registra a sessão e só então relê a senha da conta. A ordem importa: a troca confirma a
 * transação e depois encerra as sessões registradas. Se a releitura ainda vê a senha antiga, a
 * sessão já estava registrada quando a troca foi encerrá-las; se vê a nova, o login falha aqui.
 */
class RegistroDeSessao extends RegisterSessionAuthenticationStrategy {

    private final SessionRegistry sessoes;
    private final UsuarioDetailsService contas;

    RegistroDeSessao(SessionRegistry sessoes, UsuarioDetailsService contas) {
        super(sessoes);
        this.sessoes = sessoes;
        this.contas = contas;
    }

    @Override
    public void onAuthentication(Authentication autenticacao, HttpServletRequest request, HttpServletResponse response) {
        super.onAuthentication(autenticacao, request, response);

        if (contas.senhaVigente(autenticacao).isPresent())
            return;
        SessionInformation sessao = sessoes.getSessionInformation(request.getSession().getId());
        if (sessao != null)
            sessao.expireNow();
        throw new SessionAuthenticationException("A senha da conta mudou durante o login.");
    }
}
