package br.edu.ifto.aula1.security;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/** Encerra as sessões abertas de uma conta, para que uma senha antiga não continue valendo. */
@Service
public class SessoesService {

    private final SessionRegistry sessoes;

    public SessoesService(SessionRegistry sessoes) {
        this.sessoes = sessoes;
    }

    /** "exceto" é o id da sessão que continua aberta (a de quem trocou a senha), ou null. */
    public void encerrar(String login, String exceto) {
        sessoes.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails conta && conta.getUsername().equals(login))
                .flatMap(principal -> sessoes.getAllSessions(principal, false).stream())
                .filter(sessao -> !sessao.getSessionId().equals(exceto))
                .forEach(sessao -> sessao.expireNow());
    }
}
