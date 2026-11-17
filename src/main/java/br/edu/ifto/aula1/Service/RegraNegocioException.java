package br.edu.ifto.aula1.Service;

/**
 * Lançada pelos serviços quando a operação pedida viola uma regra de negócio
 * (horário duplicado, horário indisponível, transição de status inválida...).
 * A mensagem é escrita para o usuário final. O campo, quando informado, é o nome do
 * campo do formulário ao qual o erro se refere.
 *
 * Como é uma RuntimeException, a transação em andamento é desfeita.
 */
public class RegraNegocioException extends RuntimeException {

    private final String campo;

    public RegraNegocioException(String mensagem) {
        this(null, mensagem);
    }

    public RegraNegocioException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    /** Campo do formulário relacionado ao erro, ou null quando o erro é da operação toda. */
    public String getCampo() {
        return campo;
    }
}
