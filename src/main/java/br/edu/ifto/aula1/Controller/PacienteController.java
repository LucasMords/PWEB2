package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
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
@RequestMapping("pacientes")
public class PacienteController {

    @Autowired
    PacienteRepository repository;

    @Autowired
    ConsultaRepository consultaRepository;

    @GetMapping("/form")
    public ModelAndView form(ModelMap model) {
        model.addAttribute("paciente", new Paciente());
        return new ModelAndView("paciente/formulario", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("pacientes", repository.findAll());
        return new ModelAndView("paciente/lista", model);
    }

    @PostMapping("/save")
    public ModelAndView save(Paciente paciente) {
        repository.save(paciente);
        return new ModelAndView("redirect:/pacientes/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("paciente", buscarPaciente(id));
        return new ModelAndView("paciente/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(Paciente paciente) {
        Paciente pacienteBanco = buscarPaciente(paciente.getId());

        pacienteBanco.setNome(paciente.getNome());
        pacienteBanco.setCpf(paciente.getCpf());
        pacienteBanco.setDataNascimento(paciente.getDataNascimento());
        pacienteBanco.setTelefone(paciente.getTelefone());
        pacienteBanco.setEmail(paciente.getEmail());

        repository.save(pacienteBanco);
        return new ModelAndView("redirect:/pacientes/list");
    }

    @GetMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id) {
        repository.deleteById(id);
        return new ModelAndView("redirect:/pacientes/list");
    }

    @GetMapping("/consultas/{id}")
    public ModelAndView consultas(@PathVariable("id") Long id, ModelMap model) {
        Paciente paciente = buscarPaciente(id);

        model.addAttribute("titulo", "Consultas do paciente " + paciente.getNome());
        model.addAttribute("consultas", consultaRepository.findByPacienteIdOrderByDataHoraDesc(id));

        return new ModelAndView("consulta/lista", model);
    }

    private Paciente buscarPaciente(Long id) {
        return repository.findById(id).orElseThrow();
    }
}
