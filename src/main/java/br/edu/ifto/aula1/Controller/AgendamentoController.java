package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Dto.AgendamentoFiltro;
import br.edu.ifto.aula1.Dto.AgendamentoForm;
import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.StatusAgendamento;
import br.edu.ifto.aula1.Security.Papeis;
import br.edu.ifto.aula1.Service.AgendaService;
import br.edu.ifto.aula1.Service.AgendamentoService;
import br.edu.ifto.aula1.Service.MedicoService;
import br.edu.ifto.aula1.Service.PacienteService;
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

    @PostMapping("/cancelar/{id}")
    public ModelAndView cancelar(@PathVariable("id") Long id, Authentication autenticacao,
                                 RedirectAttributes attributes) {
        if (Papeis.daEquipe(autenticacao))
            service.cancelar(id);
        else
            service.cancelarDoPaciente(id, pacienteAutenticado(autenticacao).getId());

        attributes.addFlashAttribute("mensagem", "Agendamento cancelado. O horário voltou a ficar disponível.");
        return new ModelAndView("redirect:" + listaDe(autenticacao));
    }

    /** Só administração e recepção chegam aqui (SecurityConfiguration). */
    @PostMapping("/confirmar/{id}")
    public ModelAndView confirmar(@PathVariable("id") Long id, RedirectAttributes attributes) {
        service.confirmarPresenca(id);
        attributes.addFlashAttribute("mensagem", "Presença confirmada. A consulta foi registrada.");
        return new ModelAndView("redirect:/agendamentos/list");
    }

    /** O paciente não compareceu. Também só administração e recepção (SecurityConfiguration). */
    @PostMapping("/falta/{id}")
    public ModelAndView registrarFalta(@PathVariable("id") Long id, RedirectAttributes attributes) {
        service.registrarFalta(id);
        attributes.addFlashAttribute("mensagem", "Falta registrada.");
        return new ModelAndView("redirect:/agendamentos/list");
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
