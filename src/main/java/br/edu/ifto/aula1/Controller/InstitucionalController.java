package br.edu.ifto.aula1.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Páginas institucionais, públicas. */
@Controller
public class InstitucionalController {

    /** Política de privacidade (LGPD), lida antes do cadastro. */
    @GetMapping("/privacidade")
    public String privacidade() {
        return "privacidade";
    }
}
