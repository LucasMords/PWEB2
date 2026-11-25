package br.edu.ifto.aula1;

import br.edu.ifto.aula1.dto.AgendaForm;
import br.edu.ifto.aula1.model.entity.Agenda;
import br.edu.ifto.aula1.model.entity.Agendamento;
import br.edu.ifto.aula1.model.entity.Consulta;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.model.entity.StatusAgenda;
import br.edu.ifto.aula1.model.entity.StatusAgendamento;
import br.edu.ifto.aula1.model.repository.AgendaRepository;
import br.edu.ifto.aula1.model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.model.repository.ConsultaRepository;
import br.edu.ifto.aula1.model.repository.MedicoRepository;
import br.edu.ifto.aula1.model.repository.PacienteRepository;
import br.edu.ifto.aula1.service.AgendaService;
import br.edu.ifto.aula1.service.AgendamentoService;
import br.edu.ifto.aula1.service.RegraNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxos de agenda, agendamento, cancelamento e confirmação de presença contra o H2 em
 * memória, do controller ao banco. Os testes não são transacionais de propósito: cada
 * operação faz o próprio commit, como em produção, e o teste confere o que ficou gravado.
 * Cada teste usa um horário próprio, em data distante, para não interferir nos demais.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgendaIntegrationTests {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired private MockMvc mvc;
    @Autowired private AgendaService agendaService;
    @Autowired private AgendamentoService agendamentoService;
    @Autowired private AgendaRepository agendas;
    @Autowired private AgendamentoRepository agendamentos;
    @Autowired private ConsultaRepository consultas;
    @Autowired private MedicoRepository medicos;
    @Autowired private PacienteRepository pacientes;

    private final RequestPostProcessor recepcao = user("atendente").roles("ATENDENTE");
    private final RequestPostProcessor admin = user("admin").roles("ADMIN", "ATENDENTE");
    private final RequestPostProcessor contaDaAna = httpBasic("ana.souza", "Paciente123");
    private final RequestPostProcessor contaDoCarlos = httpBasic("carlos.lima", "Paciente123");
    private final RequestPostProcessor contaDaBeatriz = httpBasic("beatriz.rocha", "Medico123");
    private final RequestPostProcessor contaDoEduardo = httpBasic("eduardo.melo", "Medico123");

    private Medico beatriz;
    private Medico eduardo;
    private Paciente ana;
    private Paciente carlos;

    @BeforeEach
    void carregarPessoas() {
        beatriz = medicos.findByUsuarioLogin("beatriz.rocha").orElseThrow();
        eduardo = medicos.findByUsuarioLogin("eduardo.melo").orElseThrow();
        ana = pacientes.findByUsuarioLogin("ana.souza").orElseThrow();
        carlos = pacientes.findByUsuarioLogin("carlos.lima").orElseThrow();
    }

    // ---------- Cadastro e consulta de horários ----------

    @Test
    void medicoCadastraHorarioENaoConsegueDuplicar() throws Exception {
        LocalDate data = dataLivre();

        mvc.perform(post("/agenda/save").with(csrf()).with(contaDaBeatriz)
                        .param("data", data.toString()).param("horario", "08:30"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agenda/form"))
                .andExpect(flash().attribute("mensagem", "Horário cadastrado com sucesso."));

        assertThat(agendas.existsByMedicoIdAndDataAndHorario(beatriz.getId(), data, LocalTime.of(8, 30))).isTrue();
        long total = agendas.count();

        // Mesmo médico, data e horário: o formulário volta com a mensagem no campo e nada é gravado.
        mvc.perform(post("/agenda/save").with(csrf()).with(contaDaBeatriz)
                        .param("data", data.toString()).param("horario", "08:30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Este médico já tem um horário cadastrado nesta data e hora.")));
        assertThat(agendas.count()).isEqualTo(total);

        // Outro médico pode ter o mesmo horário.
        mvc.perform(post("/agenda/save").with(csrf()).with(contaDoEduardo)
                        .param("data", data.toString()).param("horario", "08:30"))
                .andExpect(status().is3xxRedirection());
        assertThat(agendas.count()).isEqualTo(total + 1);
    }

    @Test
    void soOMedicoCadastraHorariosNemAtendenteNemAdministracao() throws Exception {
        LocalDate data = dataLivre();
        long total = agendas.count();

        for (RequestPostProcessor conta : List.of(recepcao, admin, contaDaAna)) {
            mvc.perform(get("/agenda/form").with(conta)).andExpect(status().isForbidden());
            mvc.perform(get("/agenda/lote").with(conta)).andExpect(status().isForbidden());
            mvc.perform(post("/agenda/save").with(csrf()).with(conta)
                            .param("medicoId", beatriz.getId().toString())
                            .param("data", data.toString()).param("horario", "08:00"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/agenda/lote").with(csrf()).with(conta)
                            .param("medicoId", beatriz.getId().toString())
                            .param("dataInicio", data.toString()).param("dataFim", data.toString())
                            .param("diasSemana", data.getDayOfWeek().name())
                            .param("horaInicio", "08:00").param("horaFim", "09:00").param("duracaoMinutos", "30"))
                    .andExpect(status().isForbidden());
        }
        assertThat(agendas.count()).isEqualTo(total);

        // Na lista de horários, o atendente não vê os botões de cadastro; o médico vê.
        mvc.perform(get("/agenda/list").with(recepcao))
                .andExpect(content().string(not(containsString("href=\"/agenda/form\""))));
        mvc.perform(get("/agenda/list").with(contaDaBeatriz))
                .andExpect(content().string(containsString("href=\"/agenda/form\"")));
    }

    @Test
    void restricaoUnicaDoBancoBarraHorarioDuplicadoMesmoSemPassarPeloServico() {
        Agenda agenda = novoHorario(beatriz);

        assertThatThrownBy(() -> agendas.saveAndFlush(new Agenda(beatriz, agenda.getData(), agenda.getHorario())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cadastroDeHorarioValidaCamposEDataNoPassado() throws Exception {
        long total = agendas.count();

        mvc.perform(post("/agenda/save").with(csrf()).with(contaDaBeatriz))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe a data.")))
                .andExpect(content().string(containsString("Informe o horário.")));
        mvc.perform(post("/agenda/save").with(csrf()).with(contaDaBeatriz)
                        .param("data", LocalDate.now().minusDays(1).toString()).param("horario", "08:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A data não pode estar no passado.")));

        assertThat(agendas.count()).isEqualTo(total);
    }

    @Test
    void medicoSoCadastraAPropriaAgendaEPacienteNaoCadastraHorarios() throws Exception {
        LocalDate data = dataLivre();

        // A Dra. Beatriz tenta cadastrar na agenda do Dr. Eduardo: o id enviado é descartado.
        mvc.perform(post("/agenda/save").with(csrf()).with(contaDaBeatriz)
                        .param("medicoId", eduardo.getId().toString())
                        .param("data", data.toString()).param("horario", "11:00"))
                .andExpect(status().is3xxRedirection());
        assertThat(agendas.existsByMedicoIdAndDataAndHorario(beatriz.getId(), data, LocalTime.of(11, 0))).isTrue();
        assertThat(agendas.existsByMedicoIdAndDataAndHorario(eduardo.getId(), data, LocalTime.of(11, 0))).isFalse();

        mvc.perform(get("/agenda/form").with(contaDaBeatriz))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"medicoId\""))));

        mvc.perform(get("/agenda/form").with(contaDaAna)).andExpect(status().isForbidden());
        mvc.perform(post("/agenda/save").with(csrf()).with(contaDaAna)
                        .param("medicoId", beatriz.getId().toString())
                        .param("data", data.toString()).param("horario", "12:00"))
                .andExpect(status().isForbidden());
    }

    @Test
    void medicoGeraHorariosEmLoteEARepeticaoNaoDuplica() throws Exception {
        // Data própria, longe das usadas pelos outros testes: uma semana a partir de uma segunda-feira.
        LocalDate segunda = LocalDate.now().plusYears(5).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        long total = agendas.count();

        mvc.perform(get("/agenda/lote").with(contaDoEduardo))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"diasSemana\"")))
                .andExpect(content().string(containsString(">Seg<")));

        // Segundas e quartas, das 8h às 9h, a cada 30 minutos: 2 dias x 2 horários.
        mvc.perform(post("/agenda/lote").with(csrf()).with(contaDoEduardo)
                        .param("medicoId", eduardo.getId().toString())
                        .param("dataInicio", segunda.toString()).param("dataFim", segunda.plusDays(6).toString())
                        .param("diasSemana", "MONDAY", "WEDNESDAY")
                        .param("horaInicio", "08:00").param("horaFim", "09:00").param("duracaoMinutos", "30"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agenda/lote"))
                .andExpect(flash().attribute("mensagem", "4 horários gerados."));

        assertThat(agendas.count()).isEqualTo(total + 4);
        assertThat(agendas.existsByMedicoIdAndDataAndHorario(eduardo.getId(), segunda.plusDays(2), LocalTime.of(8, 30))).isTrue();
        assertThat(agendas.existsByMedicoIdAndDataAndHorario(eduardo.getId(), segunda.plusDays(1), LocalTime.of(8, 0))).isFalse();

        // O mesmo lote de novo: nada é gravado e o formulário explica o motivo.
        mvc.perform(post("/agenda/lote").with(csrf()).with(contaDoEduardo)
                        .param("medicoId", eduardo.getId().toString())
                        .param("dataInicio", segunda.toString()).param("dataFim", segunda.plusDays(6).toString())
                        .param("diasSemana", "MONDAY", "WEDNESDAY")
                        .param("horaInicio", "08:00").param("horaFim", "09:00").param("duracaoMinutos", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhum horário novo para gerar")));
        assertThat(agendas.count()).isEqualTo(total + 4);

        // Nenhum dia marcado (o navegador envia só o campo oculto "_diasSemana").
        mvc.perform(post("/agenda/lote").with(csrf()).with(contaDoEduardo)
                        .param("medicoId", eduardo.getId().toString())
                        .param("dataInicio", segunda.toString()).param("dataFim", segunda.plusDays(6).toString())
                        .param("_diasSemana", "on")
                        .param("horaInicio", "08:00").param("horaFim", "09:00").param("duracaoMinutos", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selecione ao menos um dia da semana.")));
    }

    @Test
    void medicoGeraLoteSoNaPropriaAgendaEPacienteNaoGera() throws Exception {
        LocalDate data = LocalDate.now().plusYears(6);

        mvc.perform(post("/agenda/lote").with(csrf()).with(contaDaBeatriz)
                        .param("medicoId", eduardo.getId().toString()) // tentativa de gerar na agenda do Dr. Eduardo
                        .param("dataInicio", data.toString()).param("dataFim", data.toString())
                        .param("diasSemana", data.getDayOfWeek().name())
                        .param("horaInicio", "14:00").param("horaFim", "15:00").param("duracaoMinutos", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("mensagem", "1 horário gerado."));

        assertThat(agendas.existsByMedicoIdAndDataAndHorario(beatriz.getId(), data, LocalTime.of(14, 0))).isTrue();
        assertThat(agendas.existsByMedicoIdAndDataAndHorario(eduardo.getId(), data, LocalTime.of(14, 0))).isFalse();

        mvc.perform(get("/agenda/lote").with(contaDaAna)).andExpect(status().isForbidden());
        mvc.perform(post("/agenda/lote").with(csrf()).with(contaDaAna)
                        .param("dataInicio", data.toString()).param("dataFim", data.toString())
                        .param("diasSemana", data.getDayOfWeek().name())
                        .param("horaInicio", "16:00").param("horaFim", "17:00").param("duracaoMinutos", "60"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listaMostraSoHorariosLivresEFuturosEAplicaOsFiltros() throws Exception {
        Agenda livre = novoHorario(beatriz);
        Agenda reservado = novoHorario(eduardo);
        agendamentoService.agendar(reservado.getId(), ana.getId());
        Agenda passado = agendas.saveAndFlush(new Agenda(beatriz, LocalDate.now().minusDays(1), horarioUnico()));

        String linkLivre = "/agendamentos/novo/" + livre.getId() + "\"";
        String linkReservado = "/agendamentos/novo/" + reservado.getId() + "\"";
        String linkPassado = "/agendamentos/novo/" + passado.getId() + "\"";

        // Filtrando pela data do horário livre: ele aparece, com médico e especialidade.
        mvc.perform(get("/agenda/list").with(recepcao).param("data", livre.getData().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(linkLivre)))
                .andExpect(content().string(containsString("Dra. Beatriz Rocha")))
                .andExpect(content().string(containsString("Cardiologia")));
        // O médico vê os horários livres, mas quem agenda é o atendente; o paciente não entra.
        mvc.perform(get("/agenda/list").with(contaDaBeatriz).param("data", livre.getData().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Dra. Beatriz Rocha<")))
                .andExpect(content().string(not(containsString(linkLivre))));
        mvc.perform(get("/agenda/list").with(contaDaAna)).andExpect(status().isForbidden());
        // O reservado e o passado não aparecem nem filtrando pela data deles.
        mvc.perform(get("/agenda/list").with(recepcao).param("data", reservado.getData().toString()))
                .andExpect(content().string(not(containsString(linkReservado))));
        mvc.perform(get("/agenda/list").with(recepcao).param("data", passado.getData().toString()))
                .andExpect(content().string(not(containsString(linkPassado))));
        // Filtros por médico e por especialidade.
        mvc.perform(get("/agenda/list").with(recepcao).param("data", livre.getData().toString())
                        .param("medicoId", eduardo.getId().toString()))
                .andExpect(content().string(not(containsString(linkLivre))));
        mvc.perform(get("/agenda/list").with(recepcao).param("data", livre.getData().toString())
                        .param("especialidade", "Cardiologia"))
                .andExpect(content().string(containsString(linkLivre)));
        mvc.perform(get("/agenda/list").with(recepcao).param("data", livre.getData().toString())
                        .param("especialidade", "Clinica Geral"))
                .andExpect(content().string(not(containsString(linkLivre))));
        // Filtro inválido não derruba a página.
        mvc.perform(get("/agenda/list").with(recepcao).param("data", "ontem").param("medicoId", "x"))
                .andExpect(status().isOk());
    }

    @Test
    void listaDeHorariosEPaginadaNoBanco() throws Exception {
        LocalDate data = dataLivre();
        for (int hora = 8; hora < 20; hora++) // 12 horários no mesmo dia: duas páginas de 10
            agendaService.cadastrar(form(beatriz, data, LocalTime.of(hora, 0)));

        mvc.perform(get("/agenda/list").with(recepcao).param("data", data.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Página 1 de 2")))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("data=" + data)))
                .andExpect(content().string(containsString(">17:00<")))
                .andExpect(content().string(not(containsString(">18:00<"))));
        mvc.perform(get("/agenda/list").with(recepcao).param("data", data.toString()).param("page", "1"))
                .andExpect(content().string(containsString("Página 2 de 2")))
                .andExpect(content().string(containsString(">18:00<")))
                .andExpect(content().string(containsString(">19:00<")));
    }

    // ---------- Agendamento ----------

    @Test
    void pacienteSoConsultaOsPropriosAgendamentosSemAgendarCancelarNemReagendar() throws Exception {
        // Data mais distante que a dos outros testes: aparece na primeira página da Ana.
        Agenda agenda = agendaService.cadastrar(form(beatriz, LocalDate.now().plusYears(9), LocalTime.of(9, 0)));
        Agenda outro = novoHorario(beatriz);
        Agendamento agendamento = agendamentoService.agendar(agenda.getId(), ana.getId());

        // Nenhuma ação de agendamento é do paciente, nem para si mesmo.
        mvc.perform(get("/agendamentos/novo/" + outro.getId()).with(contaDaAna)).andExpect(status().isForbidden());
        mvc.perform(post("/agendamentos/save").with(csrf()).with(contaDaAna)
                        .param("agendaId", outro.getId().toString()).param("pacienteId", ana.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(contaDaAna))
                .andExpect(status().isForbidden());
        mvc.perform(get("/agendamentos/reagendar/" + agendamento.getId()).with(contaDaAna))
                .andExpect(status().isForbidden());
        mvc.perform(post("/agendamentos/reagendar/" + agendamento.getId()).with(csrf()).with(contaDaAna)
                        .param("agendaId", outro.getId().toString()))
                .andExpect(status().isForbidden());
        assertThat(unicoAgendamentoDe(agenda).getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(outro.getId())).isEmpty();

        // A Ana vê o agendamento dela, sem botões de ação; o Carlos não o vê.
        String data = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(agenda.getData());
        mvc.perform(get("/usuario/agendamentos").with(contaDaAna))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(data)))
                .andExpect(content().string(containsString("entre em contato com o atendimento")))
                .andExpect(content().string(not(containsString("/agendamentos/cancelar/"))))
                .andExpect(content().string(not(containsString("/agendamentos/reagendar/"))))
                .andExpect(content().string(not(containsString("/agendamentos/confirmar/"))));
        mvc.perform(get("/usuario/agendamentos").with(contaDoCarlos))
                .andExpect(content().string(not(containsString(data))));
        // Nem o menu leva aos horários disponíveis.
        mvc.perform(get("/").with(contaDaAna))
                .andExpect(content().string(not(containsString("href=\"/agenda/list\""))));
    }

    @Test
    void atendenteAgendaParaOPacienteSemCriarConsulta() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        long consultasAntes = consultas.count();

        mvc.perform(get("/agendamentos/novo/" + agenda.getId()).with(recepcao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dra. Beatriz Rocha")))
                .andExpect(content().string(containsString("name=\"pacienteId\"")));
        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", agenda.getId().toString()).param("pacienteId", ana.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("mensagem", "Agendamento realizado com sucesso."));

        Agendamento agendamento = unicoAgendamentoDe(agenda);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(agendamentos.buscar(ana.getId(), null, agenda.getData(), null, paginaUnica()).getTotalElements()).isEqualTo(1);
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.AGENDADO);
        assertThat(consultas.count()).isEqualTo(consultasAntes);
        assertThat(consultas.existsByAgendamentoId(agendamento.getId())).isFalse();

        // O médico não agenda.
        Agenda outro = novoHorario(beatriz);
        mvc.perform(post("/agendamentos/save").with(csrf()).with(contaDaBeatriz)
                        .param("agendaId", outro.getId().toString()).param("pacienteId", ana.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void horarioReservadoNaoPodeSerAgendadoDeNovo() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        agendamentoService.agendar(agenda.getId(), ana.getId());

        // Pela tela (mesmo com a página antiga aberta) e direto no serviço.
        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", agenda.getId().toString()).param("pacienteId", carlos.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agenda/list"))
                .andExpect(flash().attribute("erro", "Este horário não está mais disponível."));
        mvc.perform(get("/agendamentos/novo/" + agenda.getId()).with(recepcao))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agenda/list"))
                .andExpect(flash().attribute("erro", "Este horário não está mais disponível."));
        assertThatThrownBy(() -> agendamentoService.agendar(agenda.getId(), carlos.getId()))
                .isInstanceOf(RegraNegocioException.class);

        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId())).hasSize(1);
    }

    @Test
    void equipeEscolheOPacienteEOCampoEObrigatorio() throws Exception {
        Agenda agenda = novoHorario(eduardo);

        mvc.perform(get("/agendamentos/novo/" + agenda.getId()).with(recepcao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"pacienteId\"")));
        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", agenda.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selecione o paciente.")));
        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", agenda.getId().toString()).param("pacienteId", "999999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paciente não encontrado.")));
        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId())).isEmpty();
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.DISPONIVEL);

        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", agenda.getId().toString())
                        .param("pacienteId", carlos.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agendamentos/list"));
        assertThat(agendamentos.buscar(carlos.getId(), null, agenda.getData(), null, paginaUnica()).getTotalElements()).isEqualTo(1);

        // Horário inexistente: 404.
        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", "999999").param("pacienteId", carlos.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void horarioPassadoNaoPodeSerAgendado() throws Exception {
        // Gravado direto no repositório: o serviço não deixaria cadastrar um horário no passado.
        Agenda passado = agendas.saveAndFlush(new Agenda(beatriz, LocalDate.now().minusDays(1), horarioUnico()));

        assertThatThrownBy(() -> agendamentoService.agendar(passado.getId(), ana.getId()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível agendar um horário que já passou.");
        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", passado.getId().toString()).param("pacienteId", ana.getId().toString()))
                .andExpect(redirectedUrl("/agenda/list"))
                .andExpect(flash().attributeExists("erro"));

        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(passado.getId())).isEmpty();
        assertThat(statusDaAgenda(passado)).isEqualTo(StatusAgenda.DISPONIVEL);
    }

    @Test
    void doisPacientesSimultaneosSoUmFicaComOHorario() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        CountDownLatch largada = new CountDownLatch(1);

        Callable<Boolean> reservaDaAna = () -> tentarAgendar(largada, agenda, ana);
        Callable<Boolean> reservaDoCarlos = () -> tentarAgendar(largada, agenda, carlos);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> primeira = executor.submit(reservaDaAna);
            Future<Boolean> segunda = executor.submit(reservaDoCarlos);
            largada.countDown();

            // Exatamente uma das duas reservas dá certo; a outra recebe "horário indisponível".
            assertThat(List.of(primeira.get(30, TimeUnit.SECONDS), segunda.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId())).hasSize(1);
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.AGENDADO);
    }

    // ---------- Confirmação de presença ----------

    @Test
    void confirmacaoDePresencaCriaUmaUnicaConsultaEMarcaComoRealizado() throws Exception {
        Agendamento agendamento = agendamentoDeOntem(beatriz, ana);
        Agenda agenda = agendamento.getAgenda();
        String url = "/agendamentos/confirmar/" + agendamento.getId();
        long consultasAntes = consultas.count();

        // Paciente (mesmo o dono do agendamento) e médico não confirmam presença; sem CSRF também não.
        mvc.perform(post(url).with(csrf()).with(contaDaAna)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(csrf()).with(contaDaBeatriz)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(recepcao)).andExpect(status().isForbidden());
        assertThat(consultas.count()).isEqualTo(consultasAntes);

        // Ver a listagem também não cria consulta.
        mvc.perform(get("/agendamentos/list").with(recepcao)).andExpect(status().isOk());
        assertThat(consultas.count()).isEqualTo(consultasAntes);

        mvc.perform(post(url).with(csrf()).with(recepcao))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("mensagem", "Presença confirmada. A consulta foi registrada."));

        Consulta consulta = consultas.findByAgendamentoId(agendamento.getId()).orElseThrow();
        assertThat(consulta.getDataRealizacao()).isNotNull();
        assertThat(consultas.count()).isEqualTo(consultasAntes + 1);
        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.REALIZADO);
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.AGENDADO);

        // Segunda confirmação: rejeitada, sem outra consulta.
        mvc.perform(post(url).with(csrf()).with(recepcao))
                .andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("erro", "A presença deste agendamento já foi confirmada."));
        assertThat(consultas.count()).isEqualTo(consultasAntes + 1);

        // Agendamento realizado não pode ser cancelado.
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "Não é possível cancelar um agendamento já realizado."));
        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.REALIZADO);

        // A consulta aparece no histórico da clínica e no da paciente, com paciente e médico.
        mvc.perform(get("/consultas/list").with(recepcao))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/consultas/edit/"))))
                .andExpect(content().string(containsString("Nº " + agendamento.getId())));
        mvc.perform(get("/usuario/consultas").with(contaDaAna))
                .andExpect(content().string(containsString("Nº " + agendamento.getId())));
    }

    @Test
    void confirmacaoAntesDoHorarioERejeitadaSemCriarConsulta() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        Agendamento agendamento = agendamentoService.agendar(agenda.getId(), ana.getId());

        mvc.perform(post("/agendamentos/confirmar/" + agendamento.getId()).with(csrf()).with(recepcao))
                .andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("erro",
                        "A presença só pode ser confirmada a partir de 1 hora antes do horário da consulta."));
        mvc.perform(post("/agendamentos/falta/" + agendamento.getId()).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "A falta só pode ser registrada depois do horário da consulta."));

        assertThat(consultas.existsByAgendamentoId(agendamento.getId())).isFalse();
        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.AGENDADO);
    }

    @Test
    void restricaoUnicaDoBancoImpedeDuasConsultasParaOMesmoAgendamento() {
        Agendamento agendamento = agendamentoDeOntem(beatriz, ana);
        agendamentoService.confirmarPresenca(agendamento.getId());

        assertThatThrownBy(() -> consultas.saveAndFlush(new Consulta(agendamento, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void observacoesSoSaoLidasEEditadasPeloMedicoQueAtendeu() throws Exception {
        Agendamento agendamento = agendamentoDeOntem(eduardo, carlos);
        Consulta consulta = agendamentoService.confirmarPresenca(agendamento.getId());

        mvc.perform(get("/consultas/edit/" + consulta.getId()).with(contaDoEduardo))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carlos Lima")))
                .andExpect(content().string(containsString("Dr. Eduardo Melo")));
        mvc.perform(post("/consultas/update").with(csrf()).with(contaDoEduardo)
                        .param("id", consulta.getId().toString()).param("observacoes", "x".repeat(501)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("As observações devem ter no máximo 500 caracteres.")));
        mvc.perform(post("/consultas/update").with(csrf()).with(contaDoEduardo)
                        .param("id", consulta.getId().toString()).param("observacoes", "Paciente orientado."))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/consultas/list"));

        // Atendimento, administração, outra médica e o próprio paciente não editam.
        for (RequestPostProcessor conta : List.of(recepcao, admin, contaDaBeatriz, contaDoCarlos)) {
            mvc.perform(get("/consultas/edit/" + consulta.getId()).with(conta)).andExpect(status().isForbidden());
            mvc.perform(post("/consultas/update").with(csrf()).with(conta)
                            .param("id", consulta.getId().toString()).param("observacoes", "Outra coisa."))
                    .andExpect(status().isForbidden());
        }
        // A equipe vê que a consulta aconteceu, mas não as observações, em nenhuma das listagens.
        for (RequestPostProcessor conta : List.of(recepcao, admin, contaDaBeatriz))
            for (String url : List.of("/consultas/list", "/pacientes/consultas/" + carlos.getId(),
                    "/medicos/consultas/" + eduardo.getId()))
                mvc.perform(get(url).with(conta))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("Nº " + agendamento.getId() + "<")))
                        .andExpect(content().string(not(containsString("Paciente orientado."))));
        // O médico que atendeu e o paciente leem.
        for (String url : List.of("/consultas/list", "/usuario/consultas", "/pacientes/consultas/" + carlos.getId(),
                "/medicos/consultas/" + eduardo.getId()))
            mvc.perform(get(url).with(contaDoEduardo))
                    .andExpect(content().string(containsString("Paciente orientado.")))
                    .andExpect(content().string(containsString("/consultas/edit/" + consulta.getId() + "\"")));
        mvc.perform(get("/usuario/consultas").with(contaDoCarlos))
                .andExpect(content().string(containsString("Paciente orientado.")))
                .andExpect(content().string(not(containsString("/consultas/edit/"))));
        // Outra paciente não lê, e paciente nenhum abre as listagens da equipe.
        mvc.perform(get("/usuario/consultas").with(contaDaAna))
                .andExpect(content().string(not(containsString("Paciente orientado."))));
        for (String url : List.of("/consultas/list", "/pacientes/consultas/" + carlos.getId(),
                "/medicos/consultas/" + eduardo.getId()))
            mvc.perform(get(url).with(contaDoCarlos)).andExpect(status().isForbidden());

        assertThat(consultas.findById(consulta.getId()).orElseThrow().getObservacoes()).isEqualTo("Paciente orientado.");
        // Não existe mais cadastro direto de consulta.
        mvc.perform(get("/consultas/form").with(recepcao)).andExpect(status().isNotFound());
    }

    // ---------- Falta ----------

    @Test
    void faltaERegistradaSoPelaRecepcaoDepoisDoHorarioESemConsulta() throws Exception {
        Agendamento agendamento = agendamentoDeOntem(beatriz, ana);
        String url = "/agendamentos/falta/" + agendamento.getId();
        long consultasAntes = consultas.count();

        // Paciente e médico não registram falta; sem CSRF também não; GET não existe.
        mvc.perform(post(url).with(csrf()).with(contaDaAna)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(csrf()).with(contaDaBeatriz)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(recepcao)).andExpect(status().isForbidden());
        mvc.perform(get(url).with(recepcao)).andExpect(status().isMethodNotAllowed());

        mvc.perform(post(url).with(csrf()).with(recepcao))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("mensagem", "Falta registrada."));

        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.FALTOU);
        assertThat(consultas.count()).isEqualTo(consultasAntes);
        assertThat(statusDaAgenda(agendamento.getAgenda())).isEqualTo(StatusAgenda.AGENDADO);

        // Depois da falta, nada mais muda: nem presença, nem cancelamento, nem outra falta.
        mvc.perform(post("/agendamentos/confirmar/" + agendamento.getId()).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "A falta deste agendamento já foi registrada."));
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "Não é possível cancelar um agendamento com falta registrada."));
        mvc.perform(post(url).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "A falta deste agendamento já foi registrada."));

        // A listagem filtra por FALTOU e mostra o selo.
        mvc.perform(get("/agendamentos/list").with(recepcao)
                        .param("data", agendamento.getAgenda().getData().toString()).param("status", "FALTOU"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Faltou<")))
                .andExpect(content().string(containsString("/agendamentos/falta/" + agendamento.getId() + "\"")));
    }

    // ---------- Cancelamento ----------

    @Test
    void atendenteCancelaMesmoDepoisDoHorarioEMedicoNao() throws Exception {
        Agendamento agendamento = agendamentoDeOntem(beatriz, ana);
        String url = "/agendamentos/cancelar/" + agendamento.getId();

        mvc.perform(post(url).with(csrf()).with(contaDaBeatriz)).andExpect(status().isForbidden());
        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.AGENDADO);

        mvc.perform(post(url).with(csrf()).with(recepcao))
                .andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attributeExists("mensagem"));
        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void cancelamentoLiberaOHorarioPreservaOHistoricoEPermiteNovoAgendamento() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        Agendamento daAna = agendamentoService.agendar(agenda.getId(), ana.getId());
        String urlCancelar = "/agendamentos/cancelar/" + daAna.getId();

        mvc.perform(post(urlCancelar).with(csrf()).with(recepcao))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attributeExists("mensagem"));

        // O registro continua no banco, cancelado, e o horário voltou a ficar livre.
        assertThat(agendamentos.findById(daAna.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.DISPONIVEL);
        mvc.perform(get("/agenda/list").with(recepcao).param("data", agenda.getData().toString()))
                .andExpect(content().string(containsString("/agendamentos/novo/" + agenda.getId() + "\"")));

        // Cancelar de novo e confirmar a presença de um cancelado são rejeitados.
        mvc.perform(post(urlCancelar).with(csrf()).with(recepcao))
                .andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("erro", "Este agendamento já foi cancelado."));
        mvc.perform(post("/agendamentos/confirmar/" + daAna.getId()).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "Não é possível confirmar a presença em um agendamento cancelado."));
        assertThat(consultas.existsByAgendamentoId(daAna.getId())).isFalse();

        // O horário liberado é marcado para o Carlos: nasce um novo registro e o da Ana segue cancelado.
        mvc.perform(post("/agendamentos/save").with(csrf()).with(recepcao)
                        .param("agendaId", agenda.getId().toString()).param("pacienteId", carlos.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agendamentos/list"));

        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId()))
                .extracting(Agendamento::getStatus)
                .containsExactly(StatusAgendamento.CANCELADO, StatusAgendamento.AGENDADO);
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.AGENDADO);

        // Um horário pode acumular vários cancelados (a restrição única só conta os ativos).
        Agendamento doCarlos = agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId()).getLast();
        agendamentoService.cancelar(doCarlos.getId(), "atendente", null);
        agendamentoService.agendar(agenda.getId(), ana.getId());
        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId()))
                .extracting(Agendamento::getStatus)
                .containsExactly(StatusAgendamento.CANCELADO, StatusAgendamento.CANCELADO, StatusAgendamento.AGENDADO);
    }

    @Test
    void cancelamentoGuardaMotivoEAutorEMostraNaListagem() throws Exception {
        // Data mais distante que a dos outros testes: "Meus agendamentos" lista do mais novo
        // para o mais antigo, então este aparece na primeira página do Carlos.
        Agenda agenda = agendaService.cadastrar(form(eduardo, LocalDate.now().plusYears(10), LocalTime.of(9, 0)));
        Agendamento agendamento = agendamentoService.agendar(agenda.getId(), carlos.getId());

        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(recepcao)
                        .param("motivo", "Médico em congresso."))
                .andExpect(redirectedUrl("/agendamentos/list"));

        Agendamento cancelado = agendamentos.findById(agendamento.getId()).orElseThrow();
        assertThat(cancelado.getMotivoCancelamento()).isEqualTo("Médico em congresso.");
        assertThat(cancelado.getCanceladoPor()).isEqualTo("atendente");
        assertThat(cancelado.getDataCancelamento()).isNotNull();

        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", agenda.getData().toString()))
                .andExpect(content().string(containsString("Médico em congresso.")))
                .andExpect(content().string(containsString("por atendente em")));
        // O paciente vê o motivo, mas não quem cancelou.
        mvc.perform(get("/usuario/agendamentos").with(contaDoCarlos))
                .andExpect(content().string(containsString("Médico em congresso.")))
                .andExpect(content().string(not(containsString("por atendente em"))));

        // Motivo acima de 255 caracteres: rejeitado, nada muda.
        Agendamento outro = agendamentoService.agendar(novoHorario(eduardo).getId(), carlos.getId());
        mvc.perform(post("/agendamentos/cancelar/" + outro.getId()).with(csrf()).with(recepcao)
                        .param("motivo", "x".repeat(256)))
                .andExpect(flash().attribute("erro", "O motivo deve ter no máximo 255 caracteres."));
        assertThat(agendamentos.findById(outro.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
    }

    // ---------- Reagendamento ----------

    @Test
    void atendenteReagendaParaOutroHorarioDoMesmoMedico() throws Exception {
        LocalDate data = dataLivre();
        Agenda atual = agendaService.cadastrar(form(beatriz, data, LocalTime.of(9, 0)));
        Agenda novo = agendaService.cadastrar(form(beatriz, data, LocalTime.of(10, 0)));
        Agenda doOutroMedico = agendaService.cadastrar(form(eduardo, data, LocalTime.of(10, 0)));
        Agendamento agendamento = agendamentoService.agendar(atual.getId(), ana.getId());
        String url = "/agendamentos/reagendar/" + agendamento.getId();

        // A tela lista só horários livres do mesmo médico.
        mvc.perform(get(url).with(recepcao).param("data", data.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"agendaId\" value=\"" + novo.getId() + "\"")))
                .andExpect(content().string(not(containsString("value=\"" + doOutroMedico.getId() + "\""))));
        // O médico não reagenda.
        mvc.perform(get(url).with(contaDaBeatriz)).andExpect(status().isForbidden());
        // Médico diferente e o mesmo horário são rejeitados.
        mvc.perform(post(url).with(csrf()).with(recepcao).param("agendaId", doOutroMedico.getId().toString()))
                .andExpect(flash().attribute("erro", "O reagendamento deve ser com o mesmo médico. "
                        + "Para trocar de médico, cancele e faça um novo agendamento."));
        mvc.perform(post(url).with(csrf()).with(recepcao).param("agendaId", atual.getId().toString()))
                .andExpect(flash().attribute("erro", "Escolha um horário diferente do atual."));

        mvc.perform(post(url).with(csrf()).with(recepcao).param("agendaId", novo.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("mensagem", "Agendamento remarcado para "
                        + data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " às 10:00."));

        Agendamento antigo = agendamentos.findById(agendamento.getId()).orElseThrow();
        assertThat(antigo.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(antigo.getMotivoCancelamento()).startsWith("Reagendado para");
        assertThat(antigo.getCanceladoPor()).isEqualTo("atendente");
        assertThat(statusDaAgenda(atual)).isEqualTo(StatusAgenda.DISPONIVEL);
        assertThat(statusDaAgenda(novo)).isEqualTo(StatusAgenda.AGENDADO);
        assertThat(unicoAgendamentoDe(novo).getStatus()).isEqualTo(StatusAgendamento.AGENDADO);

        // O agendamento antigo, já cancelado, não pode ser reagendado de novo.
        mvc.perform(get(url).with(recepcao))
                .andExpect(flash().attribute("erro", "Não é possível reagendar um agendamento cancelado."));
    }

    // ---------- Agenda do dia ----------

    @Test
    void agendaDoDiaMostraHorariosLivresEOcupadosPorMedicoEVoltaParaOMesmoDia() throws Exception {
        LocalDate data = dataLivre();
        agendaService.cadastrar(form(beatriz, data, LocalTime.of(8, 0)));
        Agenda ocupado = agendaService.cadastrar(form(beatriz, data, LocalTime.of(9, 0)));
        Agendamento agendamento = agendamentoService.agendar(ocupado.getId(), carlos.getId());

        mvc.perform(get("/agenda/dia").with(recepcao).param("data", data.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dra. Beatriz Rocha")))
                .andExpect(content().string(containsString("Carlos Lima")))
                .andExpect(content().string(containsString(">Livre<")))
                .andExpect(content().string(containsString("2 horários")))
                .andExpect(content().string(containsString("1 livres")))
                .andExpect(content().string(containsString("1 aguardando")))
                .andExpect(content().string(containsString("name=\"origem\" value=\"dia\"")));

        // O Dr. Eduardo vê só a própria agenda, mesmo pedindo a da Dra. Beatriz.
        mvc.perform(get("/agenda/dia").with(httpBasic("eduardo.melo", "Medico123"))
                        .param("data", data.toString()).param("medicoId", beatriz.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Carlos Lima"))))
                .andExpect(content().string(containsString("Nenhum horário cadastrado nesta data.")));
        // Paciente não entra; parâmetros inválidos não derrubam a página.
        mvc.perform(get("/agenda/dia").with(contaDaAna)).andExpect(status().isForbidden());
        mvc.perform(get("/agenda/dia").with(recepcao).param("data", "ontem").param("medicoId", "x"))
                .andExpect(status().isOk());

        // Ação feita na agenda do dia: volta para o mesmo dia, inclusive em caso de erro.
        mvc.perform(post("/agendamentos/confirmar/" + agendamento.getId()).with(csrf()).with(recepcao)
                        .param("origem", "dia").param("data", data.toString()))
                .andExpect(redirectedUrl("/agenda/dia?data=" + data))
                .andExpect(flash().attributeExists("erro")); // ainda não é o horário
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(recepcao)
                        .param("origem", "dia").param("data", data.toString()))
                .andExpect(redirectedUrl("/agenda/dia?data=" + data))
                .andExpect(flash().attributeExists("mensagem"));
        // O destino nunca vem da requisição: data que não é data volta para a agenda de hoje.
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(recepcao)
                        .param("origem", "dia").param("data", "//sitemalicioso.com"))
                .andExpect(redirectedUrl("/agenda/dia"));
    }

    // ---------- Gerenciamento e acesso ----------

    @Test
    void gerenciamentoListaFiltraEMostraAsAcoesConformeOPerfil() throws Exception {
        Agenda agenda = novoHorario(eduardo);
        Agendamento agendamento = agendamentoService.agendar(agenda.getId(), carlos.getId());
        String cancelar = "/agendamentos/cancelar/" + agendamento.getId() + "\"";
        String confirmar = "/agendamentos/confirmar/" + agendamento.getId() + "\"";
        String data = agenda.getData().toString();

        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", data))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carlos Lima")))
                .andExpect(content().string(containsString("Dr. Eduardo Melo")))
                .andExpect(content().string(containsString(">Agendado<")))
                .andExpect(content().string(containsString(cancelar)))
                .andExpect(content().string(containsString(confirmar)));

        // Filtros por paciente, médico e situação.
        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", data)
                        .param("pacienteId", carlos.getId().toString()).param("medicoId", eduardo.getId().toString())
                        .param("status", "AGENDADO"))
                .andExpect(content().string(containsString(cancelar)));
        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", data)
                        .param("pacienteId", ana.getId().toString()))
                .andExpect(content().string(not(containsString(cancelar))));
        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", data)
                        .param("medicoId", beatriz.getId().toString()))
                .andExpect(content().string(not(containsString(cancelar))));
        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", data).param("status", "CANCELADO"))
                .andExpect(content().string(not(containsString(cancelar))));
        mvc.perform(get("/agendamentos/list").with(recepcao).param("status", "INVALIDO").param("data", "x"))
                .andExpect(status().isOk());

        // O médico consulta a lista, mas as ações são do atendimento; o paciente não entra.
        mvc.perform(get("/agendamentos/list").with(contaDoEduardo).param("data", data))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carlos Lima")))
                .andExpect(content().string(not(containsString(cancelar))))
                .andExpect(content().string(not(containsString(confirmar))));
        mvc.perform(get("/agendamentos/list").with(contaDoCarlos)).andExpect(status().isForbidden());

        // Cancelamento exige POST com CSRF.
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(recepcao))
                .andExpect(status().isForbidden());
        mvc.perform(get("/agendamentos/cancelar/" + agendamento.getId()).with(recepcao))
                .andExpect(status().isMethodNotAllowed());
        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.AGENDADO);
    }

    @Test
    void listasDeAgendamentosSaoPaginadasMantendoOsFiltros() throws Exception {
        Paciente mariana = pacientes.findByUsuarioLogin("mariana.alves").orElseThrow();
        LocalDate data = dataLivre();
        for (int hora = 8; hora < 20; hora++) { // 12 agendamentos da Mariana no mesmo dia
            Agenda agenda = agendaService.cadastrar(form(eduardo, data, LocalTime.of(hora, 0)));
            agendamentoService.agendar(agenda.getId(), mariana.getId());
        }

        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", data.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Página 1 de 2")))
                .andExpect(content().string(containsString("/agendamentos/list?page=1")))
                .andExpect(content().string(containsString("data=" + data)));
        mvc.perform(get("/agendamentos/list").with(recepcao).param("data", data.toString()).param("page", "1"))
                .andExpect(content().string(containsString("Página 2 de 2")));

        // "Meus agendamentos" não tem filtros, mas pagina do mesmo jeito.
        mvc.perform(get("/usuario/agendamentos").with(httpBasic("mariana.alves", "Paciente123")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Página 1 de 2")))
                .andExpect(content().string(containsString("/usuario/agendamentos?page=1")));
    }

    @Test
    void medicoOuPacienteComHistoricoNaAgendaNaoPodeSerExcluido() throws Exception {
        Agenda agenda = novoHorario(eduardo);
        agendamentoService.agendar(agenda.getId(), carlos.getId());

        mvc.perform(post("/medicos/remove/" + eduardo.getId()).with(csrf()).with(admin))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/medicos/list"))
                .andExpect(flash().attribute("erro", "Não é possível excluir um médico que tem horários na agenda."));
        mvc.perform(post("/pacientes/remove/" + carlos.getId()).with(csrf()).with(admin))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/pacientes/list"))
                .andExpect(flash().attributeExists("erro"));

        assertThat(medicos.existsById(eduardo.getId())).isTrue();
        assertThat(pacientes.existsById(carlos.getId())).isTrue();
    }

    // ---------- Apoio ----------

    private boolean tentarAgendar(CountDownLatch largada, Agenda agenda, Paciente paciente) throws InterruptedException {
        largada.await();
        try {
            agendamentoService.agendar(agenda.getId(), paciente.getId());
            return true;
        } catch (RegraNegocioException e) {
            return false;
        }
    }

    /**
     * Um agendamento AGENDADO cujo horário foi ontem, pronto para confirmar presença ou
     * registrar falta. Gravado direto nos repositórios: o serviço não agenda horário passado.
     */
    private Agendamento agendamentoDeOntem(Medico medico, Paciente paciente) {
        Agenda agenda = new Agenda(medico, LocalDate.now().minusDays(1), horarioUnico());
        agenda.reservar();
        agendas.saveAndFlush(agenda);
        return agendamentos.saveAndFlush(new Agendamento(agenda, paciente, LocalDateTime.now().minusDays(2)));
    }

    /** Um horário livre do médico, em uma data que nenhum outro teste usa. */
    private Agenda novoHorario(Medico medico) {
        return agendaService.cadastrar(form(medico, dataLivre(), LocalTime.of(9, 0)));
    }

    private static AgendaForm form(Medico medico, LocalDate data, LocalTime horario) {
        AgendaForm form = new AgendaForm();
        form.setMedicoId(medico.getId());
        form.setData(data);
        form.setHorario(horario);
        return form;
    }

    private static LocalDate dataLivre() {
        return LocalDate.now().plusYears(2).plusDays(SEQUENCIA.incrementAndGet());
    }

    /** Horário diferente a cada chamada, para os registros gravados na data de ontem. */
    private static LocalTime horarioUnico() {
        return LocalTime.of(6, 0).plusMinutes(SEQUENCIA.incrementAndGet());
    }

    private StatusAgenda statusDaAgenda(Agenda agenda) {
        return agendas.findById(agenda.getId()).orElseThrow().getStatus();
    }

    private Agendamento unicoAgendamentoDe(Agenda agenda) {
        List<Agendamento> lista = agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId());
        assertThat(lista).hasSize(1);
        return lista.getFirst();
    }

    private static org.springframework.data.domain.Pageable paginaUnica() {
        return org.springframework.data.domain.PageRequest.of(0, 50);
    }
}
