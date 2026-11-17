package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Perfil;
import br.edu.ifto.aula1.Service.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Tratamento central das regras de negócio violadas: em vez de uma página de erro, o
 * usuário volta para a listagem da área em que estava, com a mensagem da regra (atributo
 * flash "erro"). Nenhum detalhe técnico vai para a tela; o registro fica no log.
 *
 * Registro inexistente continua sendo 404 (RegistroNaoEncontradoException).
 */
@ControllerAdvice
public class RegraNegocioAdvice {

    private static final Logger log = LoggerFactory.getLogger(RegraNegocioAdvice.class);

    @ExceptionHandler(RegraNegocioException.class)
    public String regraNegocio(RegraNegocioException e, HttpServletRequest request) {
        // Caminho dentro da aplicação, sem o context path.
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        log.info("Regra de negócio violada em {} {}: {}", request.getMethod(), caminho, e.getMessage());

        RequestContextUtils.getOutputFlashMap(request).put("erro", e.getMessage());
        return "redirect:" + destino(caminho, request);
    }

    /** Os destinos são fixos: nada da requisição entra na URL de redirecionamento. */
    private static String destino(String caminho, HttpServletRequest request) {

        // Ao tentar reservar um horário que não está mais livre, o melhor destino é escolher outro.
        if (caminho.startsWith("/agendamentos/novo") || caminho.startsWith("/agendamentos/save"))
            return "/agenda/list";
        if (caminho.startsWith("/agendamentos"))
            return daEquipe(request) ? "/agendamentos/list" : "/usuario/agendamentos";
        if (caminho.startsWith("/agenda"))
            return "/agenda/list";
        if (caminho.startsWith("/medicos"))
            return "/medicos/list";
        if (caminho.startsWith("/pacientes"))
            return "/pacientes/list";
        if (caminho.startsWith("/consultas"))
            return "/consultas/list";
        return "/";
    }

    private static boolean daEquipe(HttpServletRequest request) {
        return request.isUserInRole(Perfil.ADMIN) || request.isUserInRole(Perfil.USER)
                || request.isUserInRole(Perfil.MEDICO);
    }
}
