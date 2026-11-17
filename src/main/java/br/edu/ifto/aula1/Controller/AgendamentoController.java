package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Dto.AgendaFiltro;
import br.edu.ifto.aula1.Dto.AgendamentoFiltro;
import br.edu.ifto.aula1.Dto.AgendamentoForm;
import br.edu.ifto.aula1.Model.Entity.Agendamento;
import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.StatusAgendamento;
import br.edu.ifto.aula1.Security.Papeis;
import br.edu.ifto.aula1.Service.AgendaService;
import br.edu.ifto.aula1.Service.AgendamentoService;
import br.edu.ifto.aula1.Service.MedicoService;
import br.edu.ifto.aula1.Service.PacienteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
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
 * Agendamentos: reserva de um horário, gerenciamento, cancelamento e confirmação de presença.
 * As regras ficam no AgendamentoService; as RegraNegocioException que ele lança viram
 * mensagem de erro e redirecionamento no RegraNegocioAdvice.
 */
@Controller
@RequestMapping("agendamentos")
public class AgendamentoController {

    static final int TAMANHO_PAGINA = 10;

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

    /** Tela de confirmação: mostra o horário escolhido antes de concluir o agendamento. */
    @GetMapping("/novo/{agendaId}")
    public ModelAndView novo(@PathVariable("agendaId") Long agendaId, Authentication autenticacao, ModelMap model) {
        AgendamentoForm form = new AgendamentoForm();
        form.setAgendaId(agendaId);
        model.addAttribute("form", form);
        return confirmacao(agendaId, autenticacao, model);
    }

    @PostMapping("/save")
    public ModelAndView save(@Valid @ModelAttribute("form") AgendamentoForm form, BindingResult result,
                             Authentication autenticacao, ModelMap model, RedirectAttributes attributes) {
        boolean equipe = Papeis.daEquipe(autenticacao);

        // O paciente agenda sempre para si: o pacienteId da requisição só vale para a equipe.
        Long pacienteId = equipe ? form.getPacienteId() : pacienteAutenticado(autenticacao).getId();

        if (equipe && !result.hasFieldErrors("pacienteId")) {
            if (pacienteId == null)
                result.rejectValue("pacienteId", "NotNull", "Selecione o paciente.");
            else if (!pacienteService.existe(pacienteId))
                result.rejectValue("pacienteId", "NotFound", "Paciente não encontrado.");
        }
        if (result.hasErrors())
            return confirmacao(form.getAgendaId(), autenticacao, model);

        service.agendar(form.getAgendaId(), pacienteId);

        attributes.addFlashAttribute("mensagem", "Agendamento realizado com sucesso.");
        return new ModelAndView("redirect:" + listaDe(autenticacao));
    }

    /** Gerenciamento: todos os agendamentos, com filtros. Filtro inválido é tratado como vazio. */
    @GetMapping("/list")
    public ModelAndView listar(@ModelAttribute("filtro") AgendamentoFiltro filtro, BindingResult result,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               ModelMap model) {
        model.addAttribute("titulo", "Agendamentos");
        model.addAttribute("urlLista", "/agendamentos/list");
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
        service.cancelar(id, pacienteRestrito(autenticacao), autenticacao.getName(), motivo);

        attributes.addFlashAttribute("mensagem", "Agendamento cancelado. O horário voltou a ficar disponível.");
        return new ModelAndView("redirect:" + Retorno.agendaDoDia(request).orElse(listaDe(autenticacao)));
    }

    /** Só administração e recepção chegam aqui (SecurityConfiguration). */
    @PostMapping("/confirmar/{id}")
    public ModelAndView confirmar(@PathVariable("id") Long id, HttpServletRequest request,
                                  RedirectAttributes attributes) {
        service.confirmarPresenca(id);
        attributes.addFlashAttribute("mensagem", "Presença confirmada. A consulta foi registrada.");
        return new ModelAndView("redirect:" + Retorno.agendaDoDia(request).orElse("/agendamentos/list"));
    }

    /** O paciente não compareceu. Também só administração e recepção (SecurityConfiguration). */
    @PostMapping("/falta/{id}")
    public ModelAndView registrarFalta(@PathVariable("id") Long id, HttpServletRequest request,
                                       RedirectAttributes attributes) {
        service.registrarFalta(id);
        attributes.addFlashAttribute("mensagem", "Falta registrada.");
        return new ModelAndView("redirect:" + Retorno.agendaDoDia(request).orElse("/agendamentos/list"));
    }

    /**
     * Tela de reagendamento: o agendamento atual e os horários livres do mesmo médico.
     * O paciente só chega aqui com o próprio agendamento e dentro do prazo.
     */
    @GetMapping("/reagendar/{id}")
    public ModelAndView reagendar(@PathVariable("id") Long id,
                                  @ModelAttribute("filtro") AgendaFiltro filtro, BindingResult result,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  Authentication autenticacao, ModelMap model) {
        Agendamento agendamento = service.buscarParaReagendar(id, pacienteRestrito(autenticacao));

        // Só horários do mesmo médico: trocar de médico é cancelar e agendar de novo.
        filtro.setMedicoId(agendamento.getAgenda().getMedico().getId());
        filtro.setEspecialidade(null);

        model.addAttribute("agendamento", agendamento);
        model.addAttribute("voltar", listaDe(autenticacao));
        model.addAttribute("pagina", agendaService.listarDisponiveis(filtro, PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA)));
        return new ModelAndView("agendamento/reagendar", model);
    }

    @PostMapping("/reagendar/{id}")
    public ModelAndView confirmarReagendamento(@PathVariable("id") Long id,
                                               @RequestParam(name = "agendaId", required = false) Long agendaId,
                                               Authentication autenticacao, RedirectAttributes attributes) {
        Agendamento novo = service.reagendar(id, agendaId, pacienteRestrito(autenticacao), autenticacao.getName());

        attributes.addFlashAttribute("mensagem", "Agendamento remarcado para "
                + novo.getAgenda().getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " às " + novo.getAgenda().getHorario().format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        return new ModelAndView("redirect:" + listaDe(autenticacao));
    }

    /** null para a equipe (age em nome de qualquer paciente); o id do paciente da conta, para o paciente. */
    private Long pacienteRestrito(Authentication autenticacao) {
        return Papeis.daEquipe(autenticacao) ? null : pacienteAutenticado(autenticacao).getId();
    }

    private ModelAndView confirmacao(Long agendaId, Authentication autenticacao, ModelMap model) {
        model.addAttribute("agenda", agendaService.buscarDisponivel(agendaId));
        if (Papeis.daEquipe(autenticacao))
            model.addAttribute("pacientes", pacienteService.listar());
        else
            model.addAttribute("pacienteAutenticado", pacienteAutenticado(autenticacao));

        return new ModelAndView("agendamento/confirmacao", model);
    }

    private Paciente pacienteAutenticado(Authentication autenticacao) {
        return pacienteService.buscarPorLogin(autenticacao.getName())
                .orElseThrow(() -> new AccessDeniedException("Conta sem paciente vinculado."));
    }

    /** A equipe volta para o gerenciamento; o paciente, para os próprios agendamentos. */
    static String listaDe(Authentication autenticacao) {
        return Papeis.daEquipe(autenticacao) ? "/agendamentos/list" : "/usuario/agendamentos";
    }
}
