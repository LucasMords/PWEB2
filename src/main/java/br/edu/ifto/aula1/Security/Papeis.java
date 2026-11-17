package br.edu.ifto.aula1.Security;

import br.edu.ifto.aula1.Model.Entity.Perfil;
import org.springframework.security.core.Authentication;

import java.util.Set;

/** Perguntas sobre os perfis do usuário autenticado, usadas pelos controllers. */
public final class Papeis {

    private Papeis() {
    }

    /** ADMIN, USER (recepção) ou MEDICO: quem trabalha na clínica. */
    public static boolean daEquipe(Authentication autenticacao) {
        return temAlgum(autenticacao, Perfil.ADMIN, Perfil.USER, Perfil.MEDICO);
    }

    /** ADMIN ou USER: quem pode agir em nome de qualquer médico ou paciente. */
    public static boolean daAdministracao(Authentication autenticacao) {
        return temAlgum(autenticacao, Perfil.ADMIN, Perfil.USER);
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
