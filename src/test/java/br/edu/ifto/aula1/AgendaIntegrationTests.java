package br.edu.ifto.aula1;

import br.edu.ifto.aula1.Dto.AgendaForm;
import br.edu.ifto.aula1.Model.Entity.Agenda;
import br.edu.ifto.aula1.Model.Entity.Agendamento;
import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.StatusAgenda;
import br.edu.ifto.aula1.Model.Entity.StatusAgendamento;
import br.edu.ifto.aula1.Model.repository.AgendaRepository;
import br.edu.ifto.aula1.Model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
import br.edu.ifto.aula1.Model.repository.PacienteRepository;
import br.edu.ifto.aula1.Service.AgendaService;
import br.edu.ifto.aula1.Service.AgendamentoService;
import br.edu.ifto.aula1.Service.RegraNegocioException;
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

    private final RequestPostProcessor recepcao = user("user").roles("USER");
    private final RequestPostProcessor admin = user("admin").roles("ADMIN", "USER");
    private final RequestPostProcessor contaDaAna = httpBasic("ana.souza", "Paciente123");
    private final RequestPostProcessor contaDoCarlos = httpBasic("carlos.lima", "Paciente123");
    private final RequestPostProcessor contaDaBeatriz = httpBasic("beatriz.rocha", "Medico123");

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
    void recepcaoCadastraHorarioENaoConsegueDuplicar() throws Exception {
        LocalDate data = dataLivre();

        mvc.perform(post("/agenda/save").with(csrf()).with(recepcao)
                        .param("medicoId", beatriz.getId().toString())
                        .param("data", data.toString()).param("horario", "08:30"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agenda/form"))
                .andExpect(flash().attribute("mensagem", "Horário cadastrado com sucesso."));

        assertThat(agendas.existsByMedicoIdAndDataAndHorario(beatriz.getId(), data, LocalTime.of(8, 30))).isTrue();
        long total = agendas.count();

        // Mesmo médico, data e horário: o formulário volta com a mensagem no campo e nada é gravado.
        mvc.perform(post("/agenda/save").with(csrf()).with(recepcao)
                        .param("medicoId", beatriz.getId().toString())
                        .param("data", data.toString()).param("horario", "08:30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Este médico já tem um horário cadastrado nesta data e hora.")));
        assertThat(agendas.count()).isEqualTo(total);

        // Outro médico pode ter o mesmo horário.
        mvc.perform(post("/agenda/save").with(csrf()).with(recepcao)
                        .param("medicoId", eduardo.getId().toString())
                        .param("data", data.toString()).param("horario", "08:30"))
                .andExpect(status().is3xxRedirection());
        assertThat(agendas.count()).isEqualTo(total + 1);
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

        mvc.perform(post("/agenda/save").with(csrf()).with(recepcao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selecione o médico.")))
                .andExpect(content().string(containsString("Informe a data.")))
                .andExpect(content().string(containsString("Informe o horário.")));
        mvc.perform(post("/agenda/save").with(csrf()).with(recepcao)
                        .param("medicoId", beatriz.getId().toString())
                        .param("data", LocalDate.now().minusDays(1).toString()).param("horario", "08:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A data não pode estar no passado.")));
        mvc.perform(post("/agenda/save").with(csrf()).with(recepcao)
                        .param("medicoId", "999999")
                        .param("data", dataLivre().toString()).param("horario", "08:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Médico não encontrado.")));

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
    void listaMostraSoHorariosLivresEFuturosEAplicaOsFiltros() throws Exception {
        Agenda livre = novoHorario(beatriz);
        Agenda reservado = novoHorario(eduardo);
        agendamentoService.agendar(reservado.getId(), ana.getId());
        Agenda passado = agendas.saveAndFlush(new Agenda(beatriz, LocalDate.now().minusDays(1), horarioUnico()));

        String linkLivre = "/agendamentos/novo/" + livre.getId() + "\"";
        String linkReservado = "/agendamentos/novo/" + reservado.getId() + "\"";
        String linkPassado = "/agendamentos/novo/" + passado.getId() + "\"";

        // Filtrando pela data do horário livre: ele aparece, com médico e especialidade.
        mvc.perform(get("/agenda/list").with(contaDaAna).param("data", livre.getData().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(linkLivre)))
                .andExpect(content().string(containsString("Dra. Beatriz Rocha")))
                .andExpect(content().string(containsString("Cardiologia")));
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
    void pacienteAgendaParaSiMesmoQueEnvieOIdDeOutroPaciente() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        long consultasAntes = consultas.count();

        // Tela de confirmação: mostra o paciente da conta e não oferece a escolha de paciente.
        mvc.perform(get("/agendamentos/novo/" + agenda.getId()).with(contaDaAna))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Souza")))
                .andExpect(content().string(containsString("Dra. Beatriz Rocha")))
                .andExpect(content().string(not(containsString("name=\"pacienteId\""))));

        mvc.perform(post("/agendamentos/save").with(csrf()).with(contaDaAna)
                        .param("agendaId", agenda.getId().toString())
                        .param("pacienteId", carlos.getId().toString())) // tentativa de agendar em nome do Carlos
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/usuario/agendamentos"))
                .andExpect(flash().attribute("mensagem", "Agendamento realizado com sucesso."));

        Agendamento agendamento = unicoAgendamentoDe(agenda);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(agendamentos.buscar(ana.getId(), null, agenda.getData(), null, paginaUnica()).getTotalElements()).isEqualTo(1);
        assertThat(agendamentos.buscar(carlos.getId(), null, agenda.getData(), null, paginaUnica()).getTotalElements()).isZero();
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.AGENDADO);

        // Agendar não cria consulta.
        assertThat(consultas.count()).isEqualTo(consultasAntes);
        assertThat(consultas.existsByAgendamentoId(agendamento.getId())).isFalse();

        // A Ana vê o agendamento dela; o Carlos não.
        mvc.perform(get("/usuario/agendamentos").with(contaDaAna))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/agendamentos/cancelar/" + agendamento.getId() + "\"")))
                .andExpect(content().string(not(containsString("/agendamentos/confirmar/" + agendamento.getId() + "\""))));
        mvc.perform(get("/usuario/agendamentos").with(contaDoCarlos))
                .andExpect(content().string(not(containsString("/agendamentos/cancelar/" + agendamento.getId() + "\""))));
    }

    @Test
    void horarioReservadoNaoPodeSerAgendadoDeNovo() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        agendamentoService.agendar(agenda.getId(), ana.getId());

        // Pela tela (mesmo com a página antiga aberta) e direto no serviço.
        mvc.perform(post("/agendamentos/save").with(csrf()).with(contaDoCarlos)
                        .param("agendaId", agenda.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/agenda/list"))
                .andExpect(flash().attribute("erro", "Este horário não está mais disponível."));
        mvc.perform(get("/agendamentos/novo/" + agenda.getId()).with(contaDoCarlos))
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
        mvc.perform(post("/agendamentos/save").with(csrf()).with(contaDaAna)
                        .param("agendaId", passado.getId().toString()))
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
        Agenda agenda = novoHorario(beatriz);
        Agendamento agendamento = agendamentoService.agendar(agenda.getId(), ana.getId());
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

        // Agendamento realizado não pode ser cancelado, nem pela equipe nem pelo paciente.
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "Não é possível cancelar um agendamento já realizado."));
        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(contaDaAna))
                .andExpect(redirectedUrl("/usuario/agendamentos"))
                .andExpect(flash().attributeExists("erro"));
        assertThat(agendamentos.findById(agendamento.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusAgendamento.REALIZADO);

        // A consulta aparece no histórico da clínica e no da paciente, com paciente e médico.
        mvc.perform(get("/consultas/list").with(recepcao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/consultas/edit/" + consulta.getId() + "\"")))
                .andExpect(content().string(containsString("Nº " + agendamento.getId())));
        mvc.perform(get("/usuario/consultas").with(contaDaAna))
                .andExpect(content().string(containsString("Nº " + agendamento.getId())));
    }

    @Test
    void restricaoUnicaDoBancoImpedeDuasConsultasParaOMesmoAgendamento() {
        Agenda agenda = novoHorario(beatriz);
        Agendamento agendamento = agendamentoService.agendar(agenda.getId(), ana.getId());
        agendamentoService.confirmarPresenca(agendamento.getId());

        assertThatThrownBy(() -> consultas.saveAndFlush(new Consulta(agendamento, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void observacoesDaConsultaPodemSerEditadasPelaEquipe() throws Exception {
        Agenda agenda = novoHorario(eduardo);
        Agendamento agendamento = agendamentoService.agendar(agenda.getId(), carlos.getId());
        Consulta consulta = agendamentoService.confirmarPresenca(agendamento.getId());

        mvc.perform(get("/consultas/edit/" + consulta.getId()).with(recepcao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carlos Lima")))
                .andExpect(content().string(containsString("Dr. Eduardo Melo")));
        mvc.perform(post("/consultas/update").with(csrf()).with(recepcao)
                        .param("id", consulta.getId().toString()).param("observacoes", "x".repeat(501)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("As observações devem ter no máximo 500 caracteres.")));
        mvc.perform(post("/consultas/update").with(csrf()).with(recepcao)
                        .param("id", consulta.getId().toString()).param("observacoes", "Paciente orientado."))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/consultas/list"));

        assertThat(consultas.findById(consulta.getId()).orElseThrow().getObservacoes()).isEqualTo("Paciente orientado.");
        // Não existe mais cadastro direto de consulta.
        mvc.perform(get("/consultas/form").with(recepcao)).andExpect(status().isNotFound());
    }

    // ---------- Cancelamento ----------

    @Test
    void cancelamentoLiberaOHorarioPreservaOHistoricoEPermiteNovoAgendamento() throws Exception {
        Agenda agenda = novoHorario(beatriz);
        Agendamento daAna = agendamentoService.agendar(agenda.getId(), ana.getId());
        String urlCancelar = "/agendamentos/cancelar/" + daAna.getId();

        // Outro paciente não cancela o agendamento da Ana: para ele, é como se não existisse.
        mvc.perform(post(urlCancelar).with(csrf()).with(contaDoCarlos)).andExpect(status().isNotFound());
        assertThat(agendamentos.findById(daAna.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.AGENDADO);

        mvc.perform(post(urlCancelar).with(csrf()).with(contaDaAna))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/usuario/agendamentos"))
                .andExpect(flash().attributeExists("mensagem"));

        // O registro continua no banco, cancelado, e o horário voltou a ficar livre.
        assertThat(agendamentos.findById(daAna.getId()).orElseThrow().getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.DISPONIVEL);
        mvc.perform(get("/agenda/list").with(contaDoCarlos).param("data", agenda.getData().toString()))
                .andExpect(content().string(containsString("/agendamentos/novo/" + agenda.getId() + "\"")));

        // Cancelar de novo e confirmar a presença de um cancelado são rejeitados.
        mvc.perform(post(urlCancelar).with(csrf()).with(recepcao))
                .andExpect(redirectedUrl("/agendamentos/list"))
                .andExpect(flash().attribute("erro", "Este agendamento já foi cancelado."));
        mvc.perform(post("/agendamentos/confirmar/" + daAna.getId()).with(csrf()).with(recepcao))
                .andExpect(flash().attribute("erro", "Não é possível confirmar a presença em um agendamento cancelado."));
        assertThat(consultas.existsByAgendamentoId(daAna.getId())).isFalse();

        // O Carlos reserva o horário liberado: nasce um novo registro e o da Ana segue cancelado.
        mvc.perform(post("/agendamentos/save").with(csrf()).with(contaDoCarlos)
                        .param("agendaId", agenda.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/usuario/agendamentos"));

        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId()))
                .extracting(Agendamento::getStatus)
                .containsExactly(StatusAgendamento.CANCELADO, StatusAgendamento.AGENDADO);
        assertThat(statusDaAgenda(agenda)).isEqualTo(StatusAgenda.AGENDADO);

        // Um horário pode acumular vários cancelados (a restrição única só conta os ativos).
        Agendamento doCarlos = agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId()).getLast();
        agendamentoService.cancelar(doCarlos.getId());
        agendamentoService.agendar(agenda.getId(), ana.getId());
        assertThat(agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId()))
                .extracting(Agendamento::getStatus)
                .containsExactly(StatusAgendamento.CANCELADO, StatusAgendamento.CANCELADO, StatusAgendamento.AGENDADO);
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

        // O médico gerencia, mas não vê a ação de confirmar presença; o paciente não entra.
        mvc.perform(get("/agendamentos/list").with(httpBasic("eduardo.melo", "Medico123")).param("data", data))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(cancelar)))
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
