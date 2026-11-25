package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.dto.AgendaFiltro;
import br.edu.ifto.aula1.dto.AgendamentoFiltro;
import br.edu.ifto.aula1.dto.AgendamentoForm;
import br.edu.ifto.aula1.model.entity.Agendamento;
import br.edu.ifto.aula1.model.entity.StatusAgendamento;
import br.edu.ifto.aula1.service.AgendaService;
import br.edu.ifto.aula1.service.AgendamentoService;
import br.edu.ifto.aula1.service.MedicoService;
import br.edu.ifto.aula1.service.PacienteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;

/**
 * Agendamentos: reserva de um horário, gerenciamento, cancelamento, reagendamento,
 * confirmação de presença e registro de falta. Quem marca é o atendente, que escolhe o
 * paciente (SecurityConfiguration); o paciente só consulta os próprios agendamentos em
 * /usuario/agendamentos. As regras ficam no AgendamentoService; as RegraNegocioException
 * que ele lança viram mensagem de erro e redirecionamento no RegraNegocioAdvice.
 */
@Controller
@RequestMapping("agendamentos")
public class AgendamentoController {

    static final int TAMANHO_PAGINA = 10;
    private static final String LISTA = "/agendamentos/list";

    private final AgendamentoService service;
    private final AgendaService agendaService;
    private final PacienteService pacienteService;
    private final MedicoService medicoService;

    public AgendamentoController(AgendamentoService service, AgendaService agendaService,
                                 PacienteService pacienteService, MedicoService medicoService) {
        this.service = service;
        this.agendaService = agendaService;
        this.pacienteService = pacienteService;
        this.medicoService = medicoService;
    }

    /** Tela de confirmação: mostra o horário escolhido e pede o paciente antes de concluir. */
    @GetMapping("/novo/{agendaId}")
    public ModelAndView novo(@PathVariable("agendaId") Long agendaId, ModelMap model) {
        AgendamentoForm form = new AgendamentoForm();
        form.setAgendaId(agendaId);
        model.addAttribute("form", form);
        return confirmacao(agendaId, model);
    }

    @PostMapping("/save")
    public ModelAndView save(@Valid @ModelAttribute("form") AgendamentoForm form, BindingResult result,
                             ModelMap model, RedirectAttributes attributes) {
        Long pacienteId = form.getPacienteId();
        if (!result.hasFieldErrors("pacienteId")) {
            if (pacienteId == null)
                result.rejectValue("pacienteId", "NotNull", "Selecione o paciente.");
            else if (!pacienteService.existe(pacienteId))
                result.rejectValue("pacienteId", "NotFound", "Paciente não encontrado.");
        }
        if (result.hasErrors())
            return confirmacao(form.getAgendaId(), model);

        service.agendar(form.getAgendaId(), pacienteId);

        attributes.addFlashAttribute("mensagem", "Agendamento realizado com sucesso.");
        return new ModelAndView("redirect:" + LISTA);
    }

    /** Gerenciamento: todos os agendamentos, com filtros. Filtro inválido é tratado como vazio. */
    @GetMapping("/list")
    public ModelAndView listar(@ModelAttribute("filtro") AgendamentoFiltro filtro, BindingResult result,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               ModelMap model) {
        model.addAttribute("titulo", "Agendamentos");
        model.addAttribute("urlLista", LISTA);
        model.addAttribute("pagina", service.listar(filtro, PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA)));
        model.addAttribute("pacientes", pacienteService.listar());
        model.addAttribute("medicos", medicoService.listar());
        model.addAttribute("statusDisponiveis", StatusAgendamento.values());
        model.addAttribute("agora", service.agora());
        return new ModelAndView("agendamento/lista", model);
    }

    /** O motivo é opcional; quem cancelou (login) é registrado junto. */
    @PostMapping("/cancelar/{id}")
    public ModelAndView cancelar(@PathVariable("id") Long id,
                                 @RequestParam(name = "motivo", required = false) String motivo,
                                 Authentication autenticacao, HttpServletRequest request,
                                 RedirectAttributes attributes) {
        service.cancelar(id, autenticacao.getName(), motivo);

        attributes.addFlashAttribute("mensagem", "Agendamento cancelado. O horário voltou a ficar disponível.");
        return new ModelAndView("redirect:" + Retorno.agendaDoDia(request).orElse(LISTA));
    }

    @PostMapping("/confirmar/{id}")
    public ModelAndView confirmar(@PathVariable("id") Long id, HttpServletRequest request,
                                  RedirectAttributes attributes) {
        service.confirmarPresenca(id);
        attributes.addFlashAttribute("mensagem", "Presença confirmada. A consulta foi registrada.");
        return new ModelAndView("redirect:" + Retorno.agendaDoDia(request).orElse(LISTA));
    }

    /** O paciente não compareceu. */
    @PostMapping("/falta/{id}")
    public ModelAndView registrarFalta(@PathVariable("id") Long id, HttpServletRequest request,
                                       RedirectAttributes attributes) {
        service.registrarFalta(id);
        attributes.addFlashAttribute("mensagem", "Falta registrada.");
        return new ModelAndView("redirect:" + Retorno.agendaDoDia(request).orElse(LISTA));
    }

    /** Tela de reagendamento: o agendamento atual e os horários livres do mesmo médico. */
    @GetMapping("/reagendar/{id}")
    public ModelAndView reagendar(@PathVariable("id") Long id,
                                  @ModelAttribute("filtro") AgendaFiltro filtro, BindingResult result,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  ModelMap model) {
        Agendamento agendamento = service.buscarParaReagendar(id);

        // Só horários do mesmo médico: trocar de médico é cancelar e agendar de novo.
        filtro.setMedicoId(agendamento.getAgenda().getMedico().getId());
        filtro.setEspecialidade(null);

        model.addAttribute("agendamento", agendamento);
        model.addAttribute("voltar", LISTA);
        model.addAttribute("pagina", agendaService.listarDisponiveis(filtro, PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA)));
        return new ModelAndView("agendamento/reagendar", model);
    }

    @PostMapping("/reagendar/{id}")
    public ModelAndView confirmarReagendamento(@PathVariable("id") Long id,
                                               @RequestParam(name = "agendaId", required = false) Long agendaId,
                                               Authentication autenticacao, RedirectAttributes attributes) {
        Agendamento novo = service.reagendar(id, agendaId, autenticacao.getName());

        attributes.addFlashAttribute("mensagem", "Agendamento remarcado para "
                + novo.getAgenda().getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " às " + novo.getAgenda().getHorario().format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        return new ModelAndView("redirect:" + LISTA);
    }

    private ModelAndView confirmacao(Long agendaId, ModelMap model) {
        model.addAttribute("agenda", agendaService.buscarDisponivel(agendaId));
        model.addAttribute("pacientes", pacienteService.listar());
        return new ModelAndView("agendamento/confirmacao", model);
    }
}
