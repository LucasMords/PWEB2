package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.PacienteRepository;
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
@RequestMapping("pacientes")
public class PacienteController {
    private final EntityManager em;
    private final PacienteRepository repository;
    private final ConsultaRepository consultaRepository;

    public PacienteController(PacienteRepository repository,
                              ConsultaRepository consultaRepository, EntityManager em) {
        this.em = em;
        this.repository = repository;
        this.consultaRepository = consultaRepository;
    }

    @InitBinder("paciente")
    void campos(WebDataBinder binder) {
        binder.setAllowedFields("id", "nome", "cpf", "dataNascimento", "telefone", "email");
    }

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
    public ModelAndView save(@Validated({Default.class, Cadastro.class}) @ModelAttribute("paciente") Paciente paciente,
                             BindingResult errors, RedirectAttributes attributes) {
        if (errors.hasErrors()) return new ModelAndView("paciente/formulario");
        salvarPaciente(paciente, false, errors);
        if (errors.hasErrors()) return new ModelAndView("paciente/formulario");
        attributes.addFlashAttribute("mensagem", "Paciente cadastrado com sucesso.");
        return new ModelAndView("redirect:/pacientes/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        model.addAttribute("paciente", buscarPaciente(id));
        return new ModelAndView("paciente/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Validated({Default.class, Edicao.class}) @ModelAttribute("paciente") Paciente paciente,
                               BindingResult errors, RedirectAttributes attributes) {
        if (errors.hasErrors()) return new ModelAndView("paciente/formulario");
        salvarPaciente(paciente, true, errors);
        if (errors.hasErrors()) return new ModelAndView("paciente/formulario");
        attributes.addFlashAttribute("mensagem", "Paciente atualizado com sucesso.");
        return new ModelAndView("redirect:/pacientes/list");
    }

    @PostMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id, RedirectAttributes attributes) {
        excluirPaciente(id);
        attributes.addFlashAttribute("mensagem", "Paciente excluído com sucesso.");
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
        validarId(id);
        return repository.findById(id).orElseThrow(() -> ausente("Paciente"));
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador invalido.");
    }

    private ResponseStatusException ausente(String tipo) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, tipo + " nao encontrado.");
    }

    private void salvarPaciente(Paciente entrada, boolean edicao, BindingResult errors) {
        if (errors.hasErrors()) return;
        validar(entrada, edicao, errors);
        if (errors.hasErrors()) return;
        Paciente destino = edicao ? buscarPaciente(entrada.getId()) : new Paciente();
        destino.setNome(entrada.getNome());
        destino.setCpf(entrada.getCpf());
        destino.setDataNascimento(entrada.getDataNascimento());
        destino.setTelefone(entrada.getTelefone());
        destino.setEmail(entrada.getEmail());
        repository.save(destino);
    }

    private void excluirPaciente(Long id) {
        validarId(id);
        Paciente paciente = em.find(Paciente.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (paciente == null) throw ausente("Paciente");
        if (consultaRepository.existsByPacienteId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Paciente possui consultas vinculadas e nao pode ser excluido.");
        repository.delete(paciente);
    }

    private void validar(Paciente paciente, boolean edicao, BindingResult errors) {
        if (edicao) {
            Paciente existente = buscarPaciente(paciente.getId());
            em.lock(existente, LockModeType.PESSIMISTIC_WRITE);
            if (!errors.hasFieldErrors("dataNascimento") && paciente.getDataNascimento() != null &&
                    consultaRepository.existsByPacienteIdAndDataHoraBefore(paciente.getId(), paciente.getDataNascimento().atStartOfDay()))
                errors.rejectValue("dataNascimento", "historico", "Nascimento nao pode ser posterior as consultas do paciente.");
        }
        else if (paciente.getId() != null) errors.reject("id", "Cadastro novo nao deve informar identificador.");
        if (!errors.hasFieldErrors("cpf") && repository.findByCpf(paciente.getCpf())
                .filter(p -> !Objects.equals(p.getId(), paciente.getId())).isPresent())
            errors.rejectValue("cpf", "duplicado", "CPF ja cadastrado.");
    }
}
