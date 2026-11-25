package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.dto.AgendaFiltro;
import br.edu.ifto.aula1.dto.AgendaForm;
import br.edu.ifto.aula1.dto.AgendaLoteForm;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.security.Papeis;
import br.edu.ifto.aula1.service.AgendaDoDiaService;
import br.edu.ifto.aula1.service.AgendaService;
import br.edu.ifto.aula1.service.MedicoService;
import br.edu.ifto.aula1.service.RegraNegocioException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Horários da agenda dos médicos: cadastro de disponibilidade e consulta dos horários livres. */
@Controller
@RequestMapping("agenda")
public class AgendaController {

    private static final int TAMANHO_PAGINA = 10;

    private static final DateTimeFormatter DATA_POR_EXTENSO =
            DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.of("pt", "BR"));

    /** Dias da semana do formulário de lote, na ordem de segunda a domingo, com o nome em português. */
    private static final Map<DayOfWeek, String> DIAS_DA_SEMANA = new LinkedHashMap<>();
    static {
        for (DayOfWeek dia : DayOfWeek.values()) {
            String nome = dia.getDisplayName(TextStyle.SHORT, Locale.of("pt", "BR")).replace(".", "");
            DIAS_DA_SEMANA.put(dia, Character.toUpperCase(nome.charAt(0)) + nome.substring(1));
        }
    }

    private final AgendaService service;
    private final MedicoService medicoService;
    private final AgendaDoDiaService agendaDoDiaService;
    private final Clock clock;

    public AgendaController(AgendaService service, MedicoService medicoService,
                            AgendaDoDiaService agendaDoDiaService, Clock clock) {
        this.service = service;
        this.medicoService = medicoService;
        this.agendaDoDiaService = agendaDoDiaService;
        this.clock = clock;
    }

    /**
     * Agenda do dia: todos os horários de uma data, por médico, com o paciente e as ações
     * (confirmar presença, falta, cancelar). Sem data, mostra hoje; data inválida também.
     * O médico vê só a própria agenda; administração e atendimento podem filtrar por médico.
     */
    @GetMapping("/dia")
    public ModelAndView dia(@RequestParam(name = "data", required = false) String dataInformada,
                            @RequestParam(name = "medicoId", required = false) String medicoInformado,
                            Authentication autenticacao, ModelMap model) {
        LocalDate data = lerData(dataInformada).orElse(LocalDate.now(clock));

        Medico medicoAutenticado = medicoRestrito(autenticacao);
        Long medicoId = medicoAutenticado != null ? medicoAutenticado.getId() : lerId(medicoInformado);

        model.addAttribute("agendaDoDia", agendaDoDiaService.montar(data, medicoId));
        model.addAttribute("medicoId", medicoId);
        model.addAttribute("dataPorExtenso", data.format(DATA_POR_EXTENSO));
        model.addAttribute("hoje", LocalDate.now(clock));
        model.addAttribute("agora", LocalDateTime.now(clock));
        if (medicoAutenticado == null)
            model.addAttribute("medicos", medicoService.listar());
        return new ModelAndView("agenda/dia", model);
    }

    private static Optional<LocalDate> lerData(String texto) {
        try {
            return texto == null || texto.isBlank() ? Optional.empty() : Optional.of(LocalDate.parse(texto));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Long lerId(String texto) {
        try {
            return texto == null || texto.isBlank() ? null : Long.valueOf(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Horários disponíveis. Filtro inválido (ex.: data mal formada) é tratado como filtro vazio. */
    @GetMapping("/list")
    public ModelAndView listar(@ModelAttribute("filtro") AgendaFiltro filtro, BindingResult result,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               ModelMap model) {
        model.addAttribute("pagina", service.listarDisponiveis(filtro, PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA)));
        model.addAttribute("medicos", medicoService.listar());
        model.addAttribute("especialidades", medicoService.listarEspecialidades());
        return new ModelAndView("agenda/lista", model);
    }

    @GetMapping("/form")
    public ModelAndView form(Authentication autenticacao, ModelMap model) {
        model.addAttribute("form", new AgendaForm());
        return formulario(autenticacao, model);
    }

    @PostMapping("/save")
    public ModelAndView save(@Valid @ModelAttribute("form") AgendaForm form, BindingResult result,
                             Authentication autenticacao, ModelMap model, RedirectAttributes attributes) {
        // O médico só cadastra horários para si: o id enviado pelo formulário é descartado.
        form.setMedicoId(medicoAutenticado(autenticacao).getId());

        if (result.hasErrors())
            return formulario(autenticacao, model);

        try {
            service.cadastrar(form);
        } catch (RegraNegocioException e) {
            rejeitar(result, e);
            return formulario(autenticacao, model);
        }

        attributes.addFlashAttribute("mensagem", "Horário cadastrado com sucesso.");
        return new ModelAndView("redirect:/agenda/form");
    }

    /** Geração de vários horários de uma vez (dias da semana, faixa de horário e duração). */
    @GetMapping("/lote")
    public ModelAndView lote(Authentication autenticacao, ModelMap model) {
        AgendaLoteForm form = new AgendaLoteForm();
        form.setDataInicio(LocalDate.now());
        form.setDataFim(LocalDate.now().plusWeeks(4));
        model.addAttribute("form", form);
        return formularioLote(autenticacao, model);
    }

    @PostMapping("/lote")
    public ModelAndView gerarLote(@Valid @ModelAttribute("form") AgendaLoteForm form, BindingResult result,
                                  Authentication autenticacao, ModelMap model, RedirectAttributes attributes) {
        // Mesma regra do cadastro individual: o médico só gera horários na própria agenda.
        form.setMedicoId(medicoAutenticado(autenticacao).getId());

        if (result.hasErrors())
            return formularioLote(autenticacao, model);

        AgendaService.ResultadoLote resultado;
        try {
            resultado = service.gerarEmLote(form);
        } catch (RegraNegocioException e) {
            rejeitar(result, e);
            return formularioLote(autenticacao, model);
        }

        String mensagem = resultado.gerados() + (resultado.gerados() == 1 ? " horário gerado." : " horários gerados.");
        if (resultado.ignorados() > 0)
            mensagem += " " + resultado.ignorados() + " já estavam ocupados na agenda ou já passaram e foram ignorados.";
        attributes.addFlashAttribute("mensagem", mensagem);
        return new ModelAndView("redirect:/agenda/lote");
    }

    private ModelAndView formularioLote(Authentication autenticacao, ModelMap model) {
        model.addAttribute("diasDaSemana", DIAS_DA_SEMANA);
        formulario(autenticacao, model);
        return new ModelAndView("agenda/lote", model);
    }

    /** Regra de negócio violada: volta ao formulário com a mensagem no campo correspondente. */
    private static void rejeitar(BindingResult result, RegraNegocioException e) {
        if (e.getCampo() != null)
            result.rejectValue(e.getCampo(), "RegraNegocio", e.getMessage());
        else
            result.reject("RegraNegocio", e.getMessage());
    }

    private ModelAndView formulario(Authentication autenticacao, ModelMap model) {
        model.addAttribute("medicoAutenticado", medicoAutenticado(autenticacao));
        return new ModelAndView("agenda/formulario", model);
    }

    /**
     * Na agenda do dia, administração e atendimento veem todos os médicos (devolve null);
     * o médico vê só a própria agenda.
     */
    private Medico medicoRestrito(Authentication autenticacao) {
        return Papeis.daAdministracao(autenticacao) ? null : medicoAutenticado(autenticacao);
    }

    /** O médico dono da conta. Só o perfil MEDICO cadastra horários (SecurityConfiguration). */
    private Medico medicoAutenticado(Authentication autenticacao) {
        return medicoService.buscarPorLogin(autenticacao.getName())
                .orElseThrow(() -> new AccessDeniedException("Conta sem médico vinculado."));
    }
}
