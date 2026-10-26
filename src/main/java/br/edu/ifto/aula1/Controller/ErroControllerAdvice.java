package br.edu.ifto.aula1.Controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class ErroControllerAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView regra(ResponseStatusException erro) {
        return pagina(erro.getReason(), erro.getStatusCode().value());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView parametroInvalido() {
        return pagina("Parametro invalido. Confira os dados informados.", 400);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView integridade() {
        return pagina("Operacao nao concluida: CPF/CRM duplicado ou registro com vinculos. Confira os dados e tente novamente.", 409);
    }

    private ModelAndView pagina(String mensagem, int status) {
        ModelAndView view = new ModelAndView("erro");
        view.addObject("mensagem", mensagem);
        view.setStatus(HttpStatus.valueOf(status));
        return view;
    }
}
