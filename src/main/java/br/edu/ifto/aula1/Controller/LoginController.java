package br.edu.ifto.aula1.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Apenas exibe a página de login. O POST /login (conferir usuário e senha)
 * e o POST /logout são tratados pelo próprio Spring Security.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
