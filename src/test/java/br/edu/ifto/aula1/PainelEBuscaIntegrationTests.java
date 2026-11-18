package br.edu.ifto.aula1;

import br.edu.ifto.aula1.model.entity.Agenda;
import br.edu.ifto.aula1.model.entity.Agendamento;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.model.repository.AgendaRepository;
import br.edu.ifto.aula1.model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.model.repository.MedicoRepository;
import br.edu.ifto.aula1.model.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Painel inicial de cada perfil e busca de pacientes por nome ou CPF. */
@SpringBootTest
@AutoConfigureMockMvc
class PainelEBuscaIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private AgendaRepository agendas;
    @Autowired private AgendamentoRepository agendamentos;
    @Autowired private MedicoRepository medicos;
    @Autowired private PacienteRepository pacientes;

    private final RequestPostProcessor atendente = user("atendente").roles("ATENDENTE");

    // ---------- Painel inicial ----------

    @Test
    void atendimentoVeOResumoDeHojeEOPacienteNao() throws Exception {
        mvc.perform(get("/").with(atendente))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Abrir a agenda do dia")))
                .andExpect(content().string(containsString("aguardando")))
                .andExpect(content().string(not(containsString("Seu próximo agendamento"))));

        mvc.perform(get("/").with(httpBasic("ana.souza", "Paciente123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Seu próximo agendamento")))
                .andExpect(content().string(not(containsString("Abrir a agenda do dia"))));
    }

    @Test
    void medicoVeSoOsPropriosHorariosDeHoje() throws Exception {
        Medico eduardo = medicos.findByUsuarioLogin("eduardo.melo").orElseThrow();
        Paciente carlos = pacientes.findByUsuarioLogin("carlos.lima").orElseThrow();
        LocalDate hoje = LocalDate.now();
        // Gravados direto no banco: a regra de horário passado não interessa aqui.
        Agenda livre = agendas.save(new Agenda(eduardo, hoje, LocalTime.of(23, 58)));
        Agenda ocupada = agendas.save(new Agenda(eduardo, hoje, LocalTime.of(23, 59)));
        Agendamento agendamento = agendamentos.save(new Agendamento(ocupada, carlos, LocalDateTime.now()));
        try {
            mvc.perform(get("/").with(httpBasic("eduardo.melo", "Medico123")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Seus horários de hoje")))
                    .andExpect(content().string(containsString("23:58")))
                    .andExpect(content().string(containsString("23:59")))
                    .andExpect(content().string(containsString("Carlos Lima")));

            // A outra médica não vê os horários do Dr. Eduardo.
            mvc.perform(get("/").with(httpBasic("beatriz.rocha", "Medico123")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Seus horários de hoje")))
                    .andExpect(content().string(not(containsString("23:59"))));
        } finally {
            agendamentos.delete(agendamento);
            agendas.delete(ocupada);
            agendas.delete(livre);
        }
    }

    @Test
    void pacienteVeOProximoAgendamento() throws Exception {
        Medico beatriz = medicos.findByUsuarioLogin("beatriz.rocha").orElseThrow();
        Paciente mariana = pacientes.findByUsuarioLogin("mariana.alves").orElseThrow();
        LocalDate amanha = LocalDate.now().plusDays(1);
        Agenda agenda = agendas.save(new Agenda(beatriz, amanha, LocalTime.of(0, 1)));
        Agendamento agendamento = agendamentos.save(new Agendamento(agenda, mariana, LocalDateTime.now()));
        try {
            mvc.perform(get("/").with(httpBasic("mariana.alves", "Paciente123")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(amanha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))))
                    .andExpect(content().string(containsString("00:01")))
                    .andExpect(content().string(containsString(beatriz.getNome())));
        } finally {
            agendamentos.delete(agendamento);
            agendas.delete(agenda);
        }
    }

    // ---------- Busca de pacientes ----------

    @Test
    void buscaPacientePorParteDoNomeSemDiferenciarMaiusculas() throws Exception {
        mvc.perform(get("/pacientes/list").with(atendente).param("busca", "CARLOS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carlos Lima")))
                .andExpect(content().string(not(containsString("Ana Souza"))));
    }

    @Test
    void buscaPacientePeloCpfComOuSemPontuacao() throws Exception {
        for (String cpf : new String[]{"52998224725", "529.982.247-25", "529.982"}) {
            mvc.perform(get("/pacientes/list").with(atendente).param("busca", cpf))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Ana Souza")))
                    .andExpect(content().string(not(containsString("Carlos Lima"))));
        }
    }

    @Test
    void curingasDoLikeSaoProcuradosLiteralmente() throws Exception {
        for (String busca : new String[]{"%", "_"}) {
            mvc.perform(get("/pacientes/list").with(atendente).param("busca", busca))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Nenhum paciente encontrado.")));
        }
    }

    @Test
    void semBuscaListaTodosEmOrdemAlfabetica() throws Exception {
        mvc.perform(get("/pacientes/list").with(atendente).param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Souza")))
                .andExpect(content().string(containsString("Carlos Lima")));
    }

    @Test
    void pacienteNaoBuscaOutrosPacientes() throws Exception {
        mvc.perform(get("/pacientes/list").with(httpBasic("ana.souza", "Paciente123")).param("busca", "Carlos"))
                .andExpect(status().isForbidden());
    }
}
