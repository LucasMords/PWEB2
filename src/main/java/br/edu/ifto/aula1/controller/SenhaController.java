package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.dto.TrocaSenhaForm;
import br.edu.ifto.aula1.security.SessoesService;
import br.edu.ifto.aula1.security.TentativasLoginService;
import br.edu.ifto.aula1.service.UsuarioService;
import br.edu.ifto.aula1.validation.CredenciaisValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Troca da própria senha. A conta é sempre a autenticada: nenhuma rota recebe login ou id. */
@Controller
@RequestMapping("usuario/senha")
public class SenhaController {

    private final UsuarioService service;
    private final TentativasLoginService tentativas;
    private final SessoesService sessoes;

    public SenhaController(UsuarioService service, TentativasLoginService tentativas, SessoesService sessoes) {
        this.service = service;
        this.tentativas = tentativas;
        this.sessoes = sessoes;
    }

    @GetMapping
    public ModelAndView form(Authentication autenticacao, ModelMap model) {
        model.addAttribute("form", new TrocaSenhaForm());
        return formulario(autenticacao, model);
    }

    @PostMapping
    public ModelAndView trocar(@Valid @ModelAttribute("form") TrocaSenhaForm form, BindingResult result,
                               Authentication autenticacao, HttpServletRequest request,
                               ModelMap model, RedirectAttributes attributes) {
        String login = autenticacao.getName();

        CredenciaisValidator.validarSenha("novaSenha", form.getNovaSenha(), login, result);
        if (!result.hasFieldErrors("novaSenha") && form.getNovaSenha().equals(form.getSenhaAtual()))
            result.rejectValue("novaSenha", "Igual", "A nova senha deve ser diferente da atual.");
        if (!result.hasFieldErrors("confirmacao") && !form.getConfirmacao().equals(form.getNovaSenha()))
            result.rejectValue("confirmacao", "Diferente", "As senhas não são iguais.");

        // Uma sessão deixada aberta não pode servir para descobrir a senha por tentativa e erro:
        // as falhas contam para o mesmo bloqueio do login.
        if (!result.hasErrors()) {
            if (tentativas.bloqueada(login)) {
                result.rejectValue("senhaAtual", "Bloqueada", "Muitas tentativas. Tente de novo em alguns minutos.");
            } else if (!service.senhaConfere(login, form.getSenhaAtual())) {
                tentativas.registrarFalha(login);
                result.rejectValue("senhaAtual", "Incorreta", "A senha atual está incorreta.");
            }
        }
        if (result.hasErrors())
            return formulario(autenticacao, model);

        service.trocarSenha(login, form.getNovaSenha());
        HttpSession sessao = request.getSession(false);
        sessoes.encerrar(login, sessao == null ? null : sessao.getId());

        attributes.addFlashAttribute("mensagem", "Senha alterada com sucesso.");
        return new ModelAndView("redirect:/usuario/dados");
    }

    private ModelAndView formulario(Authentication autenticacao, ModelMap model) {
        model.addAttribute("provisoria", service.senhaProvisoria(autenticacao.getName()));
        return new ModelAndView("usuario/senha", model);
    }
}
