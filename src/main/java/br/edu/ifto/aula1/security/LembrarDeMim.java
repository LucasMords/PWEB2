package br.edu.ifto.aula1.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import java.util.UUID;

/**
 * Remember-me padrão, mas o cookie só é assinado com a senha que foi conferida no login. O
 * padrão relê a senha do banco e, se ela mudou durante o login, assinaria com a nova.
 */
class LembrarDeMim extends TokenBasedRememberMeServices {

    private final UsuarioDetailsService contas;

    LembrarDeMim(UsuarioDetailsService contas) {
        // Chave aleatória, como no padrão: os cookies valem até a aplicação reiniciar.
        super(UUID.randomUUID().toString(), contas);
        this.contas = contas;
    }

    /** Um valor aleatório produz um cookie que não vale; null faria a classe pai reler a senha. */
    @Override
    protected String retrievePassword(Authentication autenticacao) {
        return contas.senhaVigente(autenticacao).orElseGet(() -> UUID.randomUUID().toString());
    }
}
