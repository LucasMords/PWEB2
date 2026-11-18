package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.dto.ConsultaForm;
import br.edu.ifto.aula1.model.entity.Consulta;
import br.edu.ifto.aula1.service.ConsultaService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Histórico das consultas realizadas. Não há cadastro aqui: a consulta é criada quando a
 * clínica confirma a presença do paciente (AgendamentoController.confirmar). Depois disso
 * só as observações podem ser editadas.
 */
@Controller
@RequestMapping("consultas")
public class ConsultaController {

    private final ConsultaService service;

    public ConsultaController(ConsultaService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("titulo", "Histórico de consultas");
        model.addAttribute("consultas", service.listar());
        return new ModelAndView("consulta/lista", model);
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        Consulta consulta = service.buscar(id);

        ConsultaForm form = new ConsultaForm();
        form.setId(consulta.getId());
        form.setObservacoes(consulta.getObservacoes());

        model.addAttribute("form", form);
        model.addAttribute("consulta", consulta);
        return new ModelAndView("consulta/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Valid @ModelAttribute("form") ConsultaForm form, BindingResult result,
                               ModelMap model, RedirectAttributes attributes) {
        // Garante que a consulta editada existe (senão 404) antes de qualquer validação.
        Consulta consulta = service.buscar(form.getId());

        if (result.hasErrors()) {
            model.addAttribute("consulta", consulta);
            return new ModelAndView("consulta/formulario", model);
        }

        service.atualizarObservacoes(form.getId(), form.getObservacoes());
        attributes.addFlashAttribute("mensagem", "Observações atualizadas com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }
}
