package br.edu.ifto.aula1.Validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Usuário e senha digitados no cadastro de paciente ou de médico.
 * É um objeto só do formulário: a entidade Usuario guarda apenas o hash da senha
 * e não é preenchida diretamente pela requisição.
 */
public class Credenciais {

    @NotBlank(message = "Informe o usuário.")
    @Pattern(regexp = "[a-z0-9._-]{3,80}",
            message = "Use de 3 a 80 caracteres: letras minúsculas, números, ponto, hífen ou sublinhado.")
    private String login;

    @NotBlank(message = "Informe a senha.")
    @Size(min = 8, max = 72, message = "A senha deve ter de {min} a {max} caracteres.")
    @Pattern(regexp = "(?=.*\\p{L})(?=.*\\d).*", message = "A senha deve ter letras e números.")
    private String senha;

    public String getLogin() {
        return login;
    }

    // Em branco vira null para que só o @NotBlank responda pelo campo vazio.
    public void setLogin(String login) {
        this.login = (login == null || login.isBlank()) ? null : login.trim();
    }

    public String getSenha() {
        return senha;
    }

    // A senha não passa por trim(): espaços fazem parte dela.
    public void setSenha(String senha) {
        this.senha = (senha == null || senha.isBlank()) ? null : senha;
    }
}
