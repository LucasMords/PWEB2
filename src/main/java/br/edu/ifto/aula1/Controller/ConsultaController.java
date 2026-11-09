package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.StatusConsulta;
import br.edu.ifto.aula1.Service.ConsultaService;
import br.edu.ifto.aula1.Service.MedicoService;
import br.edu.ifto.aula1.Service.PacienteService;
import br.edu.ifto.aula1.Validation.Cadastro;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("consultas")
public class ConsultaController {

    private final ConsultaService service;
    private final PacienteService pacienteService;
    private final MedicoService medicoService;

    public ConsultaController(ConsultaService service, PacienteService pacienteService, MedicoService medicoService) {
        this.service = service;
        this.pacienteService = pacienteService;
        this.medicoService = medicoService;
    }

    @GetMapping("/form")
    public ModelAndView form(ModelMap model) {
        adicionarDadosFormulario(model, new Consulta());
        return new ModelAndView("consulta/formulario", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("titulo", "Todas as consultas");
        model.addAttribute("consultas", service.listar());
        return new ModelAndView("consulta/lista", model);
    }

    @PostMapping("/save")
    public ModelAndView save(@Validated({Default.class, Cadastro.class}) Consulta consulta,
                             BindingResult result,
                             ModelMap model,
                             RedirectAttributes attributes) {

        validarPacienteEMedico(consulta, result);
        if (result.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario");
        }

        // O serviço força o status AGENDADA: ele não é escolhido no cadastro.
        service.agendar(consulta);
        attributes.addFlashAttribute("mensagem", "Consulta agendada com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        adicionarDadosFormulario(model, service.buscar(id));
        return new ModelAndView("consulta/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Valid Consulta consulta,
                               BindingResult result,
                               ModelMap model,
                               RedirectAttributes attributes) {

        // Garante que a consulta editada existe (senão 404) antes de qualquer validação.
        service.buscar(consulta.getId());

        validarPacienteEMedico(consulta, result);
        if (result.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario");
        }

        service.atualizar(consulta);
        attributes.addFlashAttribute("mensagem", "Consulta atualizada com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }

    @GetMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id) {
        service.remover(id);
        return new ModelAndView("redirect:/consultas/list");
    }

    private void adicionarDadosFormulario(ModelMap model, Consulta consulta) {
        model.addAttribute("consulta", consulta);
        model.addAttribute("pacientes", pacienteService.listar());
        model.addAttribute("medicos", medicoService.listar());
        model.addAttribute("statusDisponiveis", StatusConsulta.values());
    }

    private void validarPacienteEMedico(Consulta consulta, BindingResult result) {
        if (consulta.getPaciente() == null || consulta.getPaciente().getId() == null)
            result.rejectValue("paciente.id", "NotNull", "Selecione o paciente.");
        else if (!pacienteService.existe(consulta.getPaciente().getId()))
            result.rejectValue("paciente.id", "NotFound", "Paciente não encontrado.");

        if (consulta.getMedico() == null || consulta.getMedico().getId() == null)
            result.rejectValue("medico.id", "NotNull", "Selecione o médico.");
        else if (!medicoService.existe(consulta.getMedico().getId()))
            result.rejectValue("medico.id", "NotFound", "Médico não encontrado.");
    }
}
