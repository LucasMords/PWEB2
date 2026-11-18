package br.edu.ifto.aula1.security;

import br.edu.ifto.aula1.model.entity.Perfil;
import org.springframework.security.core.Authentication;

import java.util.Set;

/** Perguntas sobre os perfis do usuário autenticado, usadas pelos controllers. */
public final class Papeis {

    private Papeis() {
    }

    /** ADMIN ou ATENDENTE: quem vê a agenda de todos os médicos. */
    public static boolean daAdministracao(Authentication autenticacao) {
        return temAlgum(autenticacao, Perfil.ADMIN, Perfil.ATENDENTE);
    }

    public static boolean temAlgum(Authentication autenticacao, String... perfis) {
        if (autenticacao == null)
            return false;

        Set<String> procurados = Set.of(perfis);
        return autenticacao.getAuthorities().stream()
                .map(autoridade -> autoridade.getAuthority())
                .anyMatch(nome -> nome != null && nome.startsWith("ROLE_")
                        && procurados.contains(nome.substring("ROLE_".length())));
    }
}
