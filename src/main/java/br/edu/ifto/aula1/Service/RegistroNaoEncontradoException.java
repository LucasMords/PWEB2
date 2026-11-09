package br.edu.ifto.aula1.Service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Lançada pelos serviços quando o registro procurado não existe.
 * O @ResponseStatus faz o Spring responder 404 (página templates/error/404.html),
 * sem que o serviço precise conhecer classes da camada web.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RegistroNaoEncontradoException extends RuntimeException {

    public RegistroNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
