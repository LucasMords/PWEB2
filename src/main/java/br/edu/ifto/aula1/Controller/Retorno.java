package br.edu.ifto.aula1.Controller;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Para onde voltar depois de uma ação sobre um agendamento. As ações podem partir da
 * listagem de agendamentos ou da agenda do dia; neste caso o formulário envia
 * origem=dia e a data exibida. O destino é sempre uma URL fixa da aplicação: da
 * requisição só entra a data, e só depois de validada como LocalDate (sem redirecionamento
 * aberto para endereços externos).
 */
final class Retorno {

    static final String ORIGEM_AGENDA_DO_DIA = "dia";

    private Retorno() {
    }

    static Optional<String> agendaDoDia(HttpServletRequest request) {
        if (!ORIGEM_AGENDA_DO_DIA.equals(request.getParameter("origem")))
            return Optional.empty();

        String data = request.getParameter("data");
        try {
            return Optional.of("/agenda/dia?data=" + LocalDate.parse(data));
        } catch (DateTimeParseException | NullPointerException e) {
            return Optional.of("/agenda/dia");
        }
    }
}
