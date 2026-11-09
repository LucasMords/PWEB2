package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.StatusConsulta;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
import br.edu.ifto.aula1.Model.repository.PacienteRepository;
import br.edu.ifto.aula1.Validation.Cadastro;
import jakarta.transaction.Transactional;
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

@Transactional
@Controller
@RequestMapping("consultas")
public class ConsultaController {

    ConsultaRepository repository;
    PacienteRepository pacienteRepository;
    MedicoRepository medicoRepository;

    public ConsultaController(ConsultaRepository repository, PacienteRepository pacienteRepository, MedicoRepository medicoRepository) {
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
        this.medicoRepository = medicoRepository;
    }

    @GetMapping("/form")
    public ModelAndView form(ModelMap model) {
        adicionarDadosFormulario(model, new Consulta());
        return new ModelAndView("consulta/formulario", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("titulo", "Todas as consultas");
        model.addAttribute("consultas", repository.findAll());
        return new ModelAndView("consulta/lista", model);
    }

    @PostMapping("/save")
    public ModelAndView save(@Validated({Default.class, Cadastro.class}) Consulta consulta,
                             BindingResult result,
                             ModelMap model,
                             RedirectAttributes attributes) {

        // O status não é escolhido no cadastro: toda consulta nova começa agendada,
        // mesmo que alguém envie outro valor na requisição.
        consulta.setId(null);
        consulta.setStatus(StatusConsulta.AGENDADA);

        validarPacienteEMedico(consulta, result);
        if (result.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario");
        }

        associarPacienteEMedico(consulta);
        repository.save(consulta);
        attributes.addFlashAttribute("mensagem", "Consulta agendada com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        adicionarDadosFormulario(model, buscarConsulta(id));
        return new ModelAndView("consulta/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Valid Consulta consulta,
                               BindingResult result,
                               ModelMap model,
                               RedirectAttributes attributes) {

        validarPacienteEMedico(consulta, result);
        if (result.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario");
        }

        associarPacienteEMedico(consulta);
        repository.save(consulta);
        attributes.addFlashAttribute("mensagem", "Consulta atualizada com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }

    @GetMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id) {
        repository.deleteById(id);
        return new ModelAndView("redirect:/consultas/list");
    }

    private void adicionarDadosFormulario(ModelMap model, Consulta consulta) {
        model.addAttribute("consulta", consulta);
        model.addAttribute("pacientes", pacienteRepository.findAll());
        model.addAttribute("medicos", medicoRepository.findAll());
        model.addAttribute("statusDisponiveis", StatusConsulta.values());
    }

    private void validarPacienteEMedico(Consulta consulta, BindingResult result) {
        if (consulta.getPaciente() == null || consulta.getPaciente().getId() == null)
            result.rejectValue("paciente.id", "NotNull", "Selecione o paciente.");
        else if (!pacienteRepository.existsById(consulta.getPaciente().getId()))
            result.rejectValue("paciente.id", "NotFound", "Paciente não encontrado.");

        if (consulta.getMedico() == null || consulta.getMedico().getId() == null)
            result.rejectValue("medico.id", "NotNull", "Selecione o médico.");
        else if (!medicoRepository.existsById(consulta.getMedico().getId()))
            result.rejectValue("medico.id", "NotFound", "Médico não encontrado.");
    }

    private void associarPacienteEMedico(Consulta consulta) {
        Long pacienteId = consulta.getPaciente().getId();
        Long medicoId = consulta.getMedico().getId();

        consulta.setPaciente(pacienteRepository.findById(pacienteId).orElseThrow());
        consulta.setMedico(medicoRepository.findById(medicoId).orElseThrow());
    }

    private Consulta buscarConsulta(Long id) {
        return repository.findById(id).orElseThrow();
    }
}
