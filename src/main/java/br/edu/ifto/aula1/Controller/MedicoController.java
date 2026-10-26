package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
import org.springframework.transaction.annotation.Transactional;
import br.edu.ifto.aula1.Validation.Cadastro;
import br.edu.ifto.aula1.Validation.Edicao;
import jakarta.validation.groups.Default;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
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
    private final EntityManager em;
    private final MedicoRepository repository;
    private final ConsultaRepository consultaRepository;

    public MedicoController(MedicoRepository repository,
                            ConsultaRepository consultaRepository, EntityManager em) {
        this.em = em;
        this.repository = repository;
        this.consultaRepository = consultaRepository;
    }

    @InitBinder("medico")
    void campos(WebDataBinder binder) {
        binder.setAllowedFields("id", "nome", "crm", "especialidade", "telefone", "email");
    }

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
    public ModelAndView save(@Validated({Default.class, Cadastro.class}) @ModelAttribute("medico") Medico medico,
                             BindingResult errors, RedirectAttributes attributes) {
        if (errors.hasErrors()) return new ModelAndView("medico/formulario");
        salvarMedico(medico, false, errors);
        if (errors.hasErrors()) return new ModelAndView("medico/formulario");
        attributes.addFlashAttribute("mensagem", "Médico cadastrado com sucesso.");
        return new ModelAndView("redirect:/medicos/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("medico", buscarMedico(id));
        return new ModelAndView("medico/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Validated({Default.class, Edicao.class}) @ModelAttribute("medico") Medico medico,
                               BindingResult errors, RedirectAttributes attributes) {
        if (errors.hasErrors()) return new ModelAndView("medico/formulario");
        salvarMedico(medico, true, errors);
        if (errors.hasErrors()) return new ModelAndView("medico/formulario");
        attributes.addFlashAttribute("mensagem", "Médico atualizado com sucesso.");
        return new ModelAndView("redirect:/medicos/list");
    }

    @PostMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id, RedirectAttributes attributes) {
        excluirMedico(id);
        attributes.addFlashAttribute("mensagem", "Médico excluído com sucesso.");
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
        validarId(id);
        return repository.findById(id).orElseThrow(() -> ausente("Medico"));
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador invalido.");
    }

    private ResponseStatusException ausente(String tipo) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, tipo + " nao encontrado.");
    }

    private void salvarMedico(Medico entrada, boolean edicao, BindingResult errors) {
        if (errors.hasErrors()) return;
        validar(entrada, edicao, errors);
        if (errors.hasErrors()) return;
        Medico destino = edicao ? buscarMedico(entrada.getId()) : new Medico();
        destino.setNome(entrada.getNome());
        destino.setCrm(entrada.getCrm());
        destino.setEspecialidade(entrada.getEspecialidade());
        destino.setTelefone(entrada.getTelefone());
        destino.setEmail(entrada.getEmail());
        repository.save(destino);
    }

    private void excluirMedico(Long id) {
        validarId(id);
        Medico medico = em.find(Medico.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (medico == null) throw ausente("Medico");
        if (consultaRepository.existsByMedicoId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Medico possui consultas vinculadas e nao pode ser excluido.");
        repository.delete(medico);
    }

    private void validar(Medico medico, boolean edicao, BindingResult errors) {
        if (edicao) em.lock(buscarMedico(medico.getId()), LockModeType.PESSIMISTIC_WRITE);
        else if (medico.getId() != null) errors.reject("id", "Cadastro novo nao deve informar identificador.");
        if (!errors.hasFieldErrors("crm") && repository.findByCrm(medico.getCrm())
                .filter(m -> !Objects.equals(m.getId(), medico.getId())).isPresent())
            errors.rejectValue("crm", "duplicado", "CRM ja cadastrado.");
    }
}
