package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.security.Papeis;
import br.edu.ifto.aula1.service.AgendaDoDiaService;
import br.edu.ifto.aula1.service.AgendamentoService;
import br.edu.ifto.aula1.service.MedicoService;
import br.edu.ifto.aula1.service.PacienteService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Página inicial com um painel conforme o perfil:
 * - administração e atendimento: o resumo de hoje de todos os médicos (aguardando, faltas...);
 * - médico: os próprios horários de hoje, com o paciente de cada um;
 * - paciente: o próximo agendamento.
 * Cada painel só usa dados que o perfil já pode ver nas outras telas.
 */
@Controller
public class HomeController {

    private final AgendaDoDiaService agendaDoDiaService;
    private final AgendamentoService agendamentoService;
    private final MedicoService medicoService;
    private final PacienteService pacienteService;
    private final Clock clock;

    public HomeController(AgendaDoDiaService agendaDoDiaService, AgendamentoService agendamentoService,
                          MedicoService medicoService, PacienteService pacienteService, Clock clock) {
        this.agendaDoDiaService = agendaDoDiaService;
        this.agendamentoService = agendamentoService;
        this.medicoService = medicoService;
        this.pacienteService = pacienteService;
        this.clock = clock;
    }

    @GetMapping("/")
    public String inicio(Authentication autenticacao, Model model) {
        LocalDate hoje = LocalDate.now(clock);
        model.addAttribute("hoje", hoje);

        if (Papeis.daAdministracao(autenticacao)) {
            model.addAttribute("resumoDoDia", agendaDoDiaService.montar(hoje, null).resumo());
        } else if (Papeis.temAlgum(autenticacao, Perfil.MEDICO)) {
            medicoService.buscarPorLogin(autenticacao.getName()).ifPresent(medico ->
                    model.addAttribute("agendaDoMedico", agendaDoDiaService.montar(hoje, medico.getId())));
        } else {
            pacienteService.buscarPorLogin(autenticacao.getName()).ifPresent(paciente -> {
                model.addAttribute("pacienteDoPainel", true);
                agendamentoService.proximoDoPaciente(paciente.getId())
                        .ifPresent(proximo -> model.addAttribute("proximoAgendamento", proximo));
            });
        }
        return "index";
    }
}
