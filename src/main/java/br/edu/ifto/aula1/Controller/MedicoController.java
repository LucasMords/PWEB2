package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Service.ConsultaService;
import br.edu.ifto.aula1.Service.MedicoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("medicos")
public class MedicoController {

    private final MedicoService service;
    private final ConsultaService consultaService;

    public MedicoController(MedicoService service, ConsultaService consultaService) {
        this.service = service;
        this.consultaService = consultaService;
    }

    @GetMapping("/form")
    public ModelAndView form(ModelMap model) {
        model.addAttribute("medico", new Medico());
        return new ModelAndView("medico/formulario", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("medicos", service.listar());
        return new ModelAndView("medico/lista", model);
    }

    @PostMapping("/save")
    public ModelAndView save(@Valid Medico medico, BindingResult result, RedirectAttributes attributes) {
        medico.setId(null); // cadastro nunca usa id vindo da requisição (nem na checagem de CRM duplicado)
        validarCrmUnico(medico, result);
        if (result.hasErrors())
            return new ModelAndView("medico/formulario");

        service.cadastrar(medico);
        attributes.addFlashAttribute("mensagem", "Médico cadastrado com sucesso.");
        return new ModelAndView("redirect:/medicos/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("medico", service.buscar(id));
        return new ModelAndView("medico/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Valid Medico medico, BindingResult result, RedirectAttributes attributes) {
        validarCrmUnico(medico, result);
        if (result.hasErrors())
            return new ModelAndView("medico/formulario");

        service.atualizar(medico);
        attributes.addFlashAttribute("mensagem", "Médico atualizado com sucesso.");
        return new ModelAndView("redirect:/medicos/list");
    }

    @GetMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id) {
        service.remover(id);
        return new ModelAndView("redirect:/medicos/list");
    }

    @GetMapping("/consultas/{id}")
    public ModelAndView consultas(@PathVariable("id") Long id, ModelMap model) {
        Medico medico = service.buscar(id);

        model.addAttribute("titulo", "Consultas do médico " + medico.getNome());
        model.addAttribute("consultas", consultaService.listarPorMedico(id));

        return new ModelAndView("consulta/lista", model);
    }

    // Como o CRM é sempre salvo no mesmo formato, a comparação pega o duplicado
    // mesmo que ele tenha sido digitado de outro jeito.
    private void validarCrmUnico(Medico medico, BindingResult result) {
        if (result.hasFieldErrors("crm"))
            return;

        if (service.crmEmUso(medico.getCrm(), medico.getId()))
            result.rejectValue("crm", "Duplicado", "Já existe um médico com este CRM.");
    }
}
