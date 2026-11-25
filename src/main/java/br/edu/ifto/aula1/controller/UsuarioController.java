package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.model.entity.Agendamento;
import br.edu.ifto.aula1.model.entity.Consulta;
import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.service.ExportacaoDadosService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import br.edu.ifto.aula1.service.AgendamentoService;
import br.edu.ifto.aula1.service.ConsultaService;
import br.edu.ifto.aula1.service.MedicoService;
import br.edu.ifto.aula1.service.PacienteService;
import br.edu.ifto.aula1.service.RegistroNaoEncontradoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

/**
 * Páginas do próprio usuário autenticado. O Spring Security injeta o UserDetails montado
 * no login; ele só guarda login e autoridades, então o restante vem do banco, pelo login.
 * Nenhuma rota recebe id: cada um só enxerga o que é seu.
 */
@Controller
@RequestMapping("usuario")
public class UsuarioController {

    private final UsuarioRepository usuarios;
    private final PacienteService pacienteService;
    private final MedicoService medicoService;
    private final ConsultaService consultaService;
    private final AgendamentoService agendamentoService;
    private final ExportacaoDadosService exportacaoDadosService;

    private static final int TAMANHO_PAGINA = 10;

    public UsuarioController(UsuarioRepository usuarios, PacienteService pacienteService,
                             MedicoService medicoService, ConsultaService consultaService,
                             AgendamentoService agendamentoService, ExportacaoDadosService exportacaoDadosService) {
        this.usuarios = usuarios;
        this.pacienteService = pacienteService;
        this.medicoService = medicoService;
        this.consultaService = consultaService;
        this.agendamentoService = agendamentoService;
        this.exportacaoDadosService = exportacaoDadosService;
    }

    @GetMapping("/dados")
    public ModelAndView dados(@AuthenticationPrincipal UserDetails autenticado, ModelMap model) {
        String login = autenticado.getUsername();
        Usuario usuario = usuarios.findComPerfisByLogin(login)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário não encontrado."));

        model.addAttribute("usuario", usuario);
        // A pessoa (paciente ou médico) a quem a conta pertence; admin e recepção não têm.
        model.addAttribute("paciente", pacienteService.buscarPorLogin(login).orElse(null));
        model.addAttribute("medico", medicoService.buscarPorLogin(login).orElse(null));
        return new ModelAndView("usuario/dados", model);
    }

    /**
     * LGPD: o paciente baixa os próprios dados em JSON. Sem id na URL: o paciente é o da
     * conta autenticada. Contas sem paciente vinculado (equipe) recebem 404.
     */
    @GetMapping(value = "/dados/exportar", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ExportacaoDadosService.Exportacao> exportarDados(@AuthenticationPrincipal UserDetails autenticado) {
        String login = autenticado.getUsername();
        Paciente paciente = pacienteService.buscarPorLogin(login)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Esta conta não pertence a um paciente."));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("meus-dados-clinica.json").build().toString())
                // Dados pessoais e de saúde: nada de cache no navegador ou em proxies.
                .cacheControl(CacheControl.noStore())
                .body(exportacaoDadosService.exportar(paciente, login));
    }

    /** Consultas do paciente ou do médico dono da conta autenticada. */
    @GetMapping("/consultas")
    public ModelAndView consultas(@AuthenticationPrincipal UserDetails autenticado, ModelMap model) {
        String login = autenticado.getUsername();

        List<Consulta> consultas = pacienteService.buscarPorLogin(login)
                .map(paciente -> consultaService.listarPorPaciente(paciente.getId()))
                .or(() -> medicoService.buscarPorLogin(login)
                        .map(medico -> consultaService.listarPorMedico(medico.getId())))
                .orElse(List.of());

        // São todas da própria pessoa: do paciente atendido ou do médico que atendeu.
        model.addAttribute("consultasProprias", true);
        model.addAttribute("medicoDaConta", consultaService.medicoDaConta(login));
        model.addAttribute("titulo", "Minhas consultas");
        model.addAttribute("consultas", consultas);
        return new ModelAndView("consulta/lista", model);
    }

    /** Agendamentos do paciente ou do médico dono da conta autenticada. */
    @GetMapping("/agendamentos")
    public ModelAndView agendamentos(@AuthenticationPrincipal UserDetails autenticado,
                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                     ModelMap model) {
        String login = autenticado.getUsername();
        Pageable pageable = PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA);

        Page<Agendamento> pagina = pacienteService.buscarPorLogin(login)
                .map(paciente -> agendamentoService.listarPorPaciente(paciente.getId(), pageable))
                .or(() -> medicoService.buscarPorLogin(login)
                        .map(medico -> agendamentoService.listarPorMedico(medico.getId(), pageable)))
                .orElse(Page.empty(pageable));

        model.addAttribute("titulo", "Meus agendamentos");
        model.addAttribute("urlLista", "/usuario/agendamentos");
        model.addAttribute("pagina", pagina);
        model.addAttribute("agora", agendamentoService.agora());
        return new ModelAndView("agendamento/lista", model);
    }
}
