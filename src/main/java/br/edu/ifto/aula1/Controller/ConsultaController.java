package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.Medico;
import java.time.LocalDateTime;
import br.edu.ifto.aula1.Model.Entity.StatusConsulta;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
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
@RequestMapping("consultas")
public class ConsultaController {
    private final EntityManager em;
    private final ConsultaRepository repository;
    private final PacienteRepository pacienteRepository;
    private final MedicoRepository medicoRepository;

    public ConsultaController(ConsultaRepository repository,
                              PacienteRepository pacienteRepository, MedicoRepository medicoRepository, EntityManager em) {
        this.em = em;
        this.repository = repository;
        this.pacienteRepository = pacienteRepository;
        this.medicoRepository = medicoRepository;
    }

    @InitBinder("consulta")
    void campos(WebDataBinder binder) {
        // O formulario seleciona paciente e medico pelo ID, sem alterar seus outros dados.
        binder.setAllowedFields("id", "paciente.id", "medico.id", "dataHora", "status", "observacoes");
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
    public ModelAndView save(@Validated({Default.class, Cadastro.class}) @ModelAttribute("consulta") Consulta consulta,
                             BindingResult errors, ModelMap model, RedirectAttributes attributes) {
        if (errors.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario", model);
        }
        salvarConsulta(consulta, false, errors);
        if (errors.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario", model);
        }
        attributes.addFlashAttribute("mensagem", "Consulta cadastrada com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }

    @GetMapping("/edit/{id}")
    public ModelAndView edit(@PathVariable("id") Long id, ModelMap model) {
        adicionarDadosFormulario(model, buscarConsulta(id));
        return new ModelAndView("consulta/formulario", model);
    }

    @PostMapping("/update")
    public ModelAndView update(@Validated({Default.class, Edicao.class}) @ModelAttribute("consulta") Consulta consulta,
                               BindingResult errors, ModelMap model, RedirectAttributes attributes) {
        if (errors.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario", model);
        }
        salvarConsulta(consulta, true, errors);
        if (errors.hasErrors()) {
            adicionarDadosFormulario(model, consulta);
            return new ModelAndView("consulta/formulario", model);
        }
        attributes.addFlashAttribute("mensagem", "Consulta atualizada com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }

    @PostMapping("/remove/{id}")
    public ModelAndView remove(@PathVariable("id") Long id, RedirectAttributes attributes) {
        excluirConsulta(id);
        attributes.addFlashAttribute("mensagem", "Consulta excluída com sucesso.");
        return new ModelAndView("redirect:/consultas/list");
    }

    private void adicionarDadosFormulario(ModelMap model, Consulta consulta) {
        model.addAttribute("consulta", consulta);
        model.addAttribute("pacientes", pacienteRepository.findAll());
        model.addAttribute("medicos", medicoRepository.findAll());
        model.addAttribute("statusDisponiveis", StatusConsulta.values());
    }

    private Consulta buscarConsulta(Long id) {
        validarId(id);
        return repository.findById(id).orElseThrow(() -> ausente("Consulta"));
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador invalido.");
    }

    private ResponseStatusException ausente(String tipo) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, tipo + " nao encontrado.");
    }

    private void salvarConsulta(Consulta entrada, boolean edicao, BindingResult errors) {
        if (errors.hasErrors()) return;

        Consulta atual = edicao ? buscarConsulta(entrada.getId()) : null;
        Paciente paciente = em.find(Paciente.class, entrada.getPaciente().getId(), LockModeType.PESSIMISTIC_WRITE);
        Medico medico = em.find(Medico.class, entrada.getMedico().getId(), LockModeType.PESSIMISTIC_WRITE);

        validarParticipantes(paciente, medico, errors);
        validarStatus(entrada, atual, errors);
        validarDataHora(entrada, atual, paciente, errors);
        if (errors.hasErrors()) return;

        validarConflitos(entrada, errors);
        if (errors.hasErrors()) return;

        Consulta destino = atual == null ? new Consulta() : atual;
        destino.setDataHora(entrada.getDataHora());
        destino.setStatus(entrada.getStatus());
        destino.setObservacoes(entrada.getObservacoes());
        destino.setPaciente(paciente);
        destino.setMedico(medico);
        repository.save(destino);
    }

    private void validarParticipantes(Paciente paciente, Medico medico, BindingResult errors) {
        if (paciente == null) errors.rejectValue("paciente.id", "invalido", "Selecione um paciente existente.");
        if (medico == null) errors.rejectValue("medico.id", "invalido", "Selecione um medico existente.");
    }

    private void validarStatus(Consulta entrada, Consulta atual, BindingResult errors) {
        if (atual == null && entrada.getStatus() != StatusConsulta.AGENDADA)
            errors.rejectValue("status", "invalido", "Uma nova consulta deve iniciar como agendada.");

        boolean finalizada = atual != null && atual.getStatus() != StatusConsulta.AGENDADA;
        if (finalizada && (agendaAlterada(entrada, atual) || atual.getStatus() != entrada.getStatus()))
            errors.reject("historico", "Consulta finalizada permite apenas alterar observacoes.");
    }

    private boolean agendaAlterada(Consulta entrada, Consulta atual) {
        return atual == null
                || !Objects.equals(atual.getDataHora(), entrada.getDataHora())
                || !Objects.equals(atual.getPaciente().getId(), entrada.getPaciente().getId())
                || !Objects.equals(atual.getMedico().getId(), entrada.getMedico().getId());
    }

    private void validarDataHora(Consulta entrada, Consulta atual, Paciente paciente, BindingResult errors) {
        boolean dataFutura = entrada.getDataHora().isAfter(LocalDateTime.now());
        boolean agendada = entrada.getStatus() == StatusConsulta.AGENDADA;

        if (agendada && agendaAlterada(entrada, atual) && !dataFutura)
            errors.rejectValue("dataHora", "passado", "Agende uma data e hora futura.");
        if (entrada.getStatus() == StatusConsulta.REALIZADA && dataFutura)
            errors.rejectValue("status", "futuro", "Consulta futura nao pode ser marcada como realizada.");
        if (paciente != null && paciente.getDataNascimento().isAfter(entrada.getDataHora().toLocalDate()))
            errors.rejectValue("dataHora", "nascimento", "Consulta anterior ao nascimento do paciente.");
    }

    private void validarConflitos(Consulta entrada, BindingResult errors) {
        if (entrada.getStatus() == StatusConsulta.CANCELADA) return;

        if (!repository.conflitoMedico(entrada.getMedico().getId(), entrada.getDataHora(), entrada.getId()).isEmpty())
            errors.rejectValue("dataHora", "conflito", "Medico ja possui consulta neste horario.");
        if (!repository.conflitoPaciente(entrada.getPaciente().getId(), entrada.getDataHora(), entrada.getId()).isEmpty())
            errors.rejectValue("dataHora", "conflito", "Paciente ja possui consulta neste horario.");
    }

    private void excluirConsulta(Long id) {
        Consulta consulta = buscarConsulta(id);
        if (consulta.getStatus() == StatusConsulta.REALIZADA)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Consulta realizada deve ser preservada no historico.");
        repository.delete(consulta);
    }
}
