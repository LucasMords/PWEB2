package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
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
@RequestMapping("medicos")
public class MedicoController {

    @Autowired
    MedicoRepository repository;

    @Autowired
    ConsultaRepository consultaRepository;

    @GetMapping("/form")
    public ModelAndView form(ModelMap model) {
        model.addAttribute("medico", new Medico());
        return new ModelAndView("medico/formulario", model);
    }

    @GetMapping("/list")
    public ModelAndView listar(ModelMap model) {
        model.addAttribute("medicos", repository.findAll());
        return new ModelAndView("medico/lista", model);
    }

    @PostMapping("/save")
    public ModelAndView save(Medico medico) {
        repository.save(medico);
        return new ModelAndView("redirect:/medicos/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("medico", buscarMedico(id));
        return new ModelAndView("medico/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(Medico medico) {
        Medico medicoBanco = buscarMedico(medico.getId());

        medicoBanco.setNome(medico.getNome());
        medicoBanco.setCrm(medico.getCrm());
        medicoBanco.setEspecialidade(medico.getEspecialidade());
        medicoBanco.setTelefone(medico.getTelefone());
        medicoBanco.setEmail(medico.getEmail());

        repository.save(medicoBanco);
        return new ModelAndView("redirect:/medicos/list");
    }

    @GetMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id) {
        repository.deleteById(id);
        return new ModelAndView("redirect:/medicos/list");
    }

    @GetMapping("/consultas/{id}")
    public ModelAndView consultas(@PathVariable("id") Long id, ModelMap model) {
        Medico medico = buscarMedico(id);

        model.addAttribute("titulo", "Consultas do médico " + medico.getNome());
        model.addAttribute("consultas", consultaRepository.findByMedicoIdOrderByDataHoraDesc(id));

        return new ModelAndView("consulta/lista", model);
    }

    private Medico buscarMedico(Long id) {
        return repository.findById(id).orElseThrow();
    }
}
