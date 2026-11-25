package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.security.SessoesService;
import br.edu.ifto.aula1.service.UsuarioService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Contas de acesso, só para ADMIN (SecurityConfiguration): redefinição da senha de quem a esqueceu. */
@Controller
@RequestMapping("contas")
public class ContaController {

    private static final int TAMANHO_PAGINA = 10;

    private final UsuarioService service;
    private final SessoesService sessoes;

    public ContaController(UsuarioService service, SessoesService sessoes) {
        this.service = service;
        this.sessoes = sessoes;
    }

    @GetMapping("/list")
    public ModelAndView listar(@RequestParam(name = "busca", required = false) String busca,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               Authentication autenticacao, ModelMap model) {
        model.addAttribute("pagina", service.listarContas(busca, page, TAMANHO_PAGINA));
        model.addAttribute("busca", busca == null ? "" : busca.strip());
        model.addAttribute("loginAtual", autenticacao.getName());
        return new ModelAndView("conta/lista", model);
    }

    /** A senha provisória segue por flash: aparece uma vez, na listagem, e não fica guardada. */
    @PostMapping("/redefinir/{id}")
    public ModelAndView redefinir(@PathVariable("id") Long id, Authentication autenticacao,
                                  RedirectAttributes attributes) {
        UsuarioService.SenhaProvisoria provisoria = service.redefinirSenha(id, autenticacao.getName());
        sessoes.encerrar(provisoria.login(), null);
        attributes.addFlashAttribute("provisoria", provisoria);
        return new ModelAndView("redirect:/contas/list");
    }
}
