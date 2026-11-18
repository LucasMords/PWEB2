package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.Perfil;
import br.edu.ifto.aula1.Service.ConsultaService;
import br.edu.ifto.aula1.Service.PacienteService;
import br.edu.ifto.aula1.Validation.Credenciais;
import br.edu.ifto.aula1.Validation.CredenciaisValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("pacientes")
public class PacienteController {

    private final PacienteService service;
    private final ConsultaService consultaService;
    private final CredenciaisValidator credenciaisValidator;

    public PacienteController(PacienteService service, ConsultaService consultaService,
                              CredenciaisValidator credenciaisValidator) {
        this.service = service;
        this.consultaService = consultaService;
        this.credenciaisValidator = credenciaisValidator;
    }

    // Formulário público: é por ele que o paciente cria o próprio cadastro e a conta de acesso.
    @GetMapping("/form")
    public ModelAndView form(ModelMap model) {
        model.addAttribute("paciente", new Paciente());
        model.addAttribute("credenciais", new Credenciais());
        return new ModelAndView("paciente/formulario", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("pacientes", service.listar());
        return new ModelAndView("paciente/lista", model);
    }

    @PostMapping("/save")
    public ModelAndView save(@Valid Paciente paciente, BindingResult result,
                             @Valid Credenciais credenciais, BindingResult resultCredenciais,
                             @RequestParam(name = "consentimento", defaultValue = "false") boolean consentimento,
                             HttpServletRequest request, ModelMap model, RedirectAttributes attributes) {
        paciente.setId(null); // cadastro nunca usa id vindo da requisição (nem na checagem de CPF duplicado)
        validarCpfUnico(paciente, result);
        credenciaisValidator.validate(credenciais, resultCredenciais);
        // LGPD: o aceite da política de privacidade é obrigatório (o serviço confere de novo).
        if (!consentimento)
            model.addAttribute("erroConsentimento", PacienteService.CONSENTIMENTO_OBRIGATORIO);
        if (result.hasErrors() || resultCredenciais.hasErrors() || !consentimento)
            return new ModelAndView("paciente/formulario", model);

        service.cadastrar(paciente, credenciais, consentimento);

        // Quem se cadastrou sozinho segue para o login; a equipe volta para a lista de pacientes.
        if (request.getUserPrincipal() == null || request.isUserInRole(Perfil.PACIENTE)) {
            attributes.addFlashAttribute("mensagem", "Cadastro realizado. Entre com o usuário e a senha que você escolheu.");
            return new ModelAndView("redirect:/login");
        }

        attributes.addFlashAttribute("mensagem", "Paciente cadastrado com sucesso.");
        return new ModelAndView("redirect:/pacientes/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("paciente", service.buscar(id));
        return new ModelAndView("paciente/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Valid Paciente paciente, BindingResult result, RedirectAttributes attributes) {
        validarCpfUnico(paciente, result);
        if (result.hasErrors())
            return new ModelAndView("paciente/formulario");

        service.atualizar(paciente);
        attributes.addFlashAttribute("mensagem", "Paciente atualizado com sucesso.");
        return new ModelAndView("redirect:/pacientes/list");
    }

    @PostMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id) {
        service.remover(id);
        return new ModelAndView("redirect:/pacientes/list");
    }

    @GetMapping("/consultas/{id}")
    public ModelAndView consultas(@PathVariable("id") Long id, ModelMap model) {
        Paciente paciente = service.buscar(id);

        model.addAttribute("titulo", "Consultas do paciente " + paciente.getNome());
        model.addAttribute("consultas", consultaService.listarPorPaciente(id));

        return new ModelAndView("consulta/lista", model);
    }

    // Como o CPF é sempre salvo no mesmo formato, a comparação pega o duplicado
    // mesmo que ele tenha sido digitado sem pontuação.
    private void validarCpfUnico(Paciente paciente, BindingResult result) {
        if (result.hasFieldErrors("cpf"))
            return;

        if (service.cpfEmUso(paciente.getCpf(), paciente.getId()))
            result.rejectValue("cpf", "Duplicado", "Já existe um paciente com este CPF.");
    }
}
