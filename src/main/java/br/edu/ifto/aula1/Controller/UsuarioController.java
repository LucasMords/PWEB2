package br.edu.ifto.aula1.Controller;

import br.edu.ifto.aula1.Model.Entity.Agendamento;
import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.Usuario;
import br.edu.ifto.aula1.Model.repository.UsuarioRepository;
import br.edu.ifto.aula1.Service.AgendamentoService;
import br.edu.ifto.aula1.Service.ConsultaService;
import br.edu.ifto.aula1.Service.MedicoService;
import br.edu.ifto.aula1.Service.PacienteService;
import br.edu.ifto.aula1.Service.RegistroNaoEncontradoException;
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

    private static final int TAMANHO_PAGINA = 10;

    public UsuarioController(UsuarioRepository usuarios, PacienteService pacienteService,
                             MedicoService medicoService, ConsultaService consultaService,
                             AgendamentoService agendamentoService) {
        this.usuarios = usuarios;
        this.pacienteService = pacienteService;
        this.medicoService = medicoService;
        this.consultaService = consultaService;
        this.agendamentoService = agendamentoService;
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

    /** Consultas do paciente ou do médico dono da conta autenticada. */
    @GetMapping("/consultas")
    public ModelAndView consultas(@AuthenticationPrincipal UserDetails autenticado, ModelMap model) {
        String login = autenticado.getUsername();

        List<Consulta> consultas = pacienteService.buscarPorLogin(login)
                .map(paciente -> consultaService.listarPorPaciente(paciente.getId()))
                .or(() -> medicoService.buscarPorLogin(login)
                        .map(medico -> consultaService.listarPorMedico(medico.getId())))
                .orElse(List.of());

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
        return new ModelAndView("agendamento/lista", model);
    }
}
