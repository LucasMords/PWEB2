package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.StatusConsulta;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
import br.edu.ifto.aula1.Model.repository.PacienteRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Transactional
@Controller
@RequestMapping("consultas")
public class ConsultaController {

    @Autowired
    ConsultaRepository repository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    MedicoRepository medicoRepository;

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
    public ModelAndView save(Consulta consulta) {
        associarPacienteEMedico(consulta);
        repository.save(consulta);
        return new ModelAndView("redirect:/consultas/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        adicionarDadosFormulario(model, buscarConsulta(id));
        return new ModelAndView("consulta/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(Consulta consulta) {
        associarPacienteEMedico(consulta);
        repository.save(consulta);
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
