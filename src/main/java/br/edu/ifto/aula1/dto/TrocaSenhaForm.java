package br.edu.ifto.aula1.dto;

import br.edu.ifto.aula1.validation.Credenciais;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Troca da própria senha. Mesmas regras do cadastro (Credenciais); nada volta preenchido na tela. */
public class TrocaSenhaForm {

    @NotBlank(message = "Informe a senha atual.")
    private String senhaAtual;

    @NotBlank(message = "Informe a nova senha.")
    @Size(min = 8, max = 72, message = Credenciais.SENHA_TAMANHO)
    @Pattern(regexp = Credenciais.SENHA_REGEX, message = Credenciais.SENHA_LETRAS_E_NUMEROS)
    private String novaSenha;

    @NotBlank(message = "Repita a nova senha.")
    private String confirmacao;

    public String getSenhaAtual() {
        return senhaAtual;
    }

    public void setSenhaAtual(String senhaAtual) {
        this.senhaAtual = vazioParaNull(senhaAtual);
    }

    public String getNovaSenha() {
        return novaSenha;
    }

    public void setNovaSenha(String novaSenha) {
        this.novaSenha = vazioParaNull(novaSenha);
    }

    public String getConfirmacao() {
        return confirmacao;
    }

    public void setConfirmacao(String confirmacao) {
        this.confirmacao = vazioParaNull(confirmacao);
    }

    // Em branco vira null para que só o @NotBlank responda pelo campo vazio.
    private static String vazioParaNull(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
