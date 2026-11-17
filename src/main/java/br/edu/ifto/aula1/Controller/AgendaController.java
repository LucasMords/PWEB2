package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Dto.AgendaFiltro;
import br.edu.ifto.aula1.Dto.AgendaForm;
import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Security.Papeis;
import br.edu.ifto.aula1.Service.AgendaService;
import br.edu.ifto.aula1.Service.MedicoService;
import br.edu.ifto.aula1.Service.RegraNegocioException;
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

/** Horários da agenda dos médicos: cadastro de disponibilidade e consulta dos horários livres. */
@Controller
@RequestMapping("agenda")
public class AgendaController {

    private static final int TAMANHO_PAGINA = 10;

    private final AgendaService service;
    private final MedicoService medicoService;

    public AgendaController(AgendaService service, MedicoService medicoService) {
        this.service = service;
        this.medicoService = medicoService;
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
        Medico medicoAutenticado = medicoRestrito(autenticacao);
        if (medicoAutenticado != null)
            form.setMedicoId(medicoAutenticado.getId());
        else if (form.getMedicoId() == null && !result.hasFieldErrors("medicoId"))
            result.rejectValue("medicoId", "NotNull", "Selecione o médico.");

        if (result.hasErrors())
            return formulario(autenticacao, model);

        try {
            service.cadastrar(form);
        } catch (RegraNegocioException e) {
            // Regra de negócio violada: volta ao formulário com a mensagem no campo correspondente.
            if (e.getCampo() != null)
                result.rejectValue(e.getCampo(), "RegraNegocio", e.getMessage());
            else
                result.reject("RegraNegocio", e.getMessage());
            return formulario(autenticacao, model);
        }

        attributes.addFlashAttribute("mensagem", "Horário cadastrado com sucesso.");
        return new ModelAndView("redirect:/agenda/form");
    }

    private ModelAndView formulario(Authentication autenticacao, ModelMap model) {
        Medico medicoAutenticado = medicoRestrito(autenticacao);
        if (medicoAutenticado != null)
            model.addAttribute("medicoAutenticado", medicoAutenticado);
        else
            model.addAttribute("medicos", medicoService.listar());

        return new ModelAndView("agenda/formulario", model);
    }

    /**
     * Para quem tem só o perfil MEDICO, devolve o médico dono da conta; para a administração
     * e a recepção, que escolhem o médico no formulário, devolve null.
     */
    private Medico medicoRestrito(Authentication autenticacao) {
        if (Papeis.daAdministracao(autenticacao))
            return null;

        return medicoService.buscarPorLogin(autenticacao.getName())
                .orElseThrow(() -> new AccessDeniedException("Conta sem médico vinculado."));
    }
}
