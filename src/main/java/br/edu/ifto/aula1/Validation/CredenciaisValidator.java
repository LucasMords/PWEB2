package br.edu.ifto.aula1.Validation;

import br.edu.ifto.aula1.Service.UsuarioService;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.nio.charset.StandardCharsets;

/**
 * Regras das credenciais que as anotações não cobrem: login único e o limite do BCrypt.
 * Os controllers chamam validate() depois do @Valid.
 */
@Component
public class CredenciaisValidator implements Validator {

    // O BCrypt só considera os primeiros 72 bytes; acentos ocupam mais de um byte.
    private static final int LIMITE_BCRYPT_EM_BYTES = 72;

    private final UsuarioService usuarios;

    public CredenciaisValidator(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return Credenciais.class.isAssignableFrom(tipo);
    }

    @Override
    public void validate(Object alvo, Errors erros) {
        Credenciais credenciais = (Credenciais) alvo;

        if (!erros.hasFieldErrors("login") && usuarios.loginEmUso(credenciais.getLogin()))
            erros.rejectValue("login", "Duplicado", "Este usuário já está em uso.");

        if (!erros.hasFieldErrors("senha")
                && credenciais.getSenha().getBytes(StandardCharsets.UTF_8).length > LIMITE_BCRYPT_EM_BYTES)
            erros.rejectValue("senha", "Longa", "A senha é longa demais.");
    }
}
