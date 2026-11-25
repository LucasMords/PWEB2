package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.service.AuditoriaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Registro de auditoria, só leitura e só para ADMIN (SecurityConfiguration). */
@Controller
@RequestMapping("auditoria")
public class AuditoriaController {

    private static final int TAMANHO_PAGINA = 25;

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    /** Filtros inválidos (ação desconhecida, data mal formada) são tratados como vazios. */
    @GetMapping
    public ModelAndView listar(@RequestParam(name = "usuario", required = false) String usuario,
                               @RequestParam(name = "acao", required = false) String acao,
                               @RequestParam(name = "de", required = false) String de,
                               @RequestParam(name = "ate", required = false) String ate,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               ModelMap model) {
        AcaoAuditoria acaoFiltro = lerAcao(acao);
        LocalDate deFiltro = lerData(de);
        LocalDate ateFiltro = lerData(ate);

        model.addAttribute("pagina", service.buscar(usuario, acaoFiltro, deFiltro, ateFiltro,
                PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA)));
        model.addAttribute("acoes", AcaoAuditoria.values());
        model.addAttribute("usuario", usuario == null ? "" : usuario.trim());
        model.addAttribute("acao", acaoFiltro);
        model.addAttribute("de", deFiltro);
        model.addAttribute("ate", ateFiltro);
        return new ModelAndView("auditoria/lista", model);
    }

    private static AcaoAuditoria lerAcao(String texto) {
        try {
            return texto == null || texto.isBlank() ? null : AcaoAuditoria.valueOf(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate lerData(String texto) {
        try {
            return texto == null || texto.isBlank() ? null : LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
