package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.dto.AgendaFiltro;
import br.edu.ifto.aula1.dto.AgendaForm;
import br.edu.ifto.aula1.dto.AgendaLoteForm;
import br.edu.ifto.aula1.model.entity.Agenda;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.model.entity.StatusAgenda;
import br.edu.ifto.aula1.model.repository.AgendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras do cadastro e da consulta de horários, com os repositórios simulados e o relógio fixo. */
@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    private static final Long MEDICO_ID = 7L;
    private static final ZoneId FUSO = ZoneId.systemDefault();
    // "Agora" dos testes: quinta-feira, 10/01/2030, 10h.
    private static final LocalDateTime AGORA = LocalDateTime.of(2030, 1, 10, 10, 0);
    private static final LocalDate HOJE = AGORA.toLocalDate();
    private static final LocalDate AMANHA = HOJE.plusDays(1);
    private static final LocalTime NOVE_HORAS = LocalTime.of(9, 0);

    @Mock private AgendaRepository repository;
    @Mock private MedicoService medicoService;
    @Mock private AuditoriaService auditoria;
    private AgendaService service;

    @BeforeEach
    void preparar() {
        service = new AgendaService(repository, medicoService, auditoria, Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    // ---------- Cadastro de um horário ----------

    @Test
    void cadastraHorarioComStatusDisponivel() {
        Medico medico = medicoExistente();
        when(repository.existsByMedicoIdAndDataAndHorario(MEDICO_ID, AMANHA, NOVE_HORAS)).thenReturn(false);
        when(repository.saveAndFlush(any(Agenda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Agenda agenda = service.cadastrar(form(MEDICO_ID, AMANHA, NOVE_HORAS));

        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
        assertThat(agenda.getMedico()).isSameAs(medico);
        assertThat(agenda.getData()).isEqualTo(AMANHA);
        assertThat(agenda.getHorario()).isEqualTo(NOVE_HORAS);
        verify(repository).saveAndFlush(agenda);
    }

    @Test
    void rejeitaHorarioDuplicadoParaOMesmoMedicoSemGravar() {
        medicoExistente();
        when(repository.existsByMedicoIdAndDataAndHorario(MEDICO_ID, AMANHA, NOVE_HORAS)).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, AMANHA, NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(AgendaService.HORARIO_DUPLICADO)
                .extracting(e -> ((RegraNegocioException) e).getCampo()).isEqualTo("horario");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void cadastroSimultaneoBarradoPelaRestricaoUnicaViraMensagemDeDuplicidade() {
        medicoExistente();
        when(repository.saveAndFlush(any(Agenda.class))).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, AMANHA, NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(AgendaService.HORARIO_DUPLICADO);
    }

    @Test
    void rejeitaHorarioNoPassado() {
        when(medicoService.existe(MEDICO_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, HOJE.minusDays(1), NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A data não pode estar no passado.");
        // Hoje, mas antes das 10h do relógio.
        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, HOJE, NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O horário já passou.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaMedicoInexistenteECamposEmBranco() {
        when(medicoService.existe(99L)).thenReturn(false);
        when(medicoService.existe(MEDICO_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.cadastrar(form(99L, AMANHA, NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Médico não encontrado.");
        assertThatThrownBy(() -> service.cadastrar(form(null, AMANHA, NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Médico não encontrado.");
        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, null, NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Informe a data.");
        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, AMANHA, null)))
                .isInstanceOf(RegraNegocioException.class).hasMessage("Informe o horário.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void cadastraComADuracaoInformadaOuComAPadrao() {
        medicoExistente();
        when(repository.saveAndFlush(any(Agenda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        assertThat(service.cadastrar(form(MEDICO_ID, AMANHA, NOVE_HORAS)).getDuracaoMinutos()).isEqualTo(30);
        for (int limite : new int[]{10, 240})
            assertThat(service.cadastrar(form(MEDICO_ID, AMANHA, NOVE_HORAS, limite)).getDuracaoMinutos()).isEqualTo(limite);
    }

    @Test
    void rejeitaDuracaoForaDosLimitesOuEmBranco() {
        when(medicoService.existe(MEDICO_ID)).thenReturn(true);

        for (Integer duracao : new Integer[]{null, 9, 241})
            assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, AMANHA, NOVE_HORAS, duracao)))
                    .isInstanceOf(RegraNegocioException.class)
                    .hasMessage(AgendaService.DURACAO_INVALIDA)
                    .extracting(e -> ((RegraNegocioException) e).getCampo()).isEqualTo("duracaoMinutos");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void consultaTerminaNoMaximoAMeiaNoite() {
        medicoExistente();
        when(repository.saveAndFlush(any(Agenda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        service.cadastrar(form(MEDICO_ID, AMANHA, LocalTime.of(23, 30), 30));
        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, AMANHA, LocalTime.of(23, 30), 31)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A consulta deve terminar até a meia-noite.");
    }

    @Test
    void rejeitaSobreposicaoParcialContidaOuQueContemOutroHorario() {
        Medico medico = medicoExistente();
        // Já cadastrado: 09:00 às 10:00.
        when(repository.findByMedicoIdAndDataBetween(MEDICO_ID, AMANHA, AMANHA))
                .thenReturn(List.of(new Agenda(medico, AMANHA, NOVE_HORAS, 60)));

        assertSobreposto("08:45", 30);  // invade o começo
        assertSobreposto("09:45", 30);  // começa antes do fim
        assertSobreposto("09:15", 30);  // contido
        assertSobreposto("08:30", 120); // contém
        verify(repository, never()).saveAndFlush(any());
        verify(medicoService, times(4)).bloquear(MEDICO_ID);
    }

    @Test
    void cadastroELoteBloqueiamOMedicoAntesDeConferirAAgenda() {
        medicoExistente();
        when(repository.saveAndFlush(any(Agenda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        service.cadastrar(form(MEDICO_ID, AMANHA, NOVE_HORAS));
        InOrder noCadastro = inOrder(medicoService, repository);
        noCadastro.verify(medicoService).bloquear(MEDICO_ID);
        noCadastro.verify(repository).existsByMedicoIdAndDataAndHorario(MEDICO_ID, AMANHA, NOVE_HORAS);
        noCadastro.verify(repository).findByMedicoIdAndDataBetween(MEDICO_ID, AMANHA, AMANHA);
        noCadastro.verify(repository).saveAndFlush(any());

        clearInvocations(medicoService, repository);
        AgendaLoteForm lote = loteValido();
        service.gerarEmLote(lote);
        InOrder noLote = inOrder(medicoService, repository);
        noLote.verify(medicoService).bloquear(MEDICO_ID);
        noLote.verify(repository).findByMedicoIdAndDataBetween(MEDICO_ID, lote.getDataInicio(), lote.getDataFim());
        noLote.verify(repository).saveAllAndFlush(anyList());
    }

    @Test
    void horariosEncostadosNaoSeSobrepoem() {
        Medico medico = medicoExistente();
        when(repository.findByMedicoIdAndDataBetween(MEDICO_ID, AMANHA, AMANHA))
                .thenReturn(List.of(new Agenda(medico, AMANHA, NOVE_HORAS, 60)));
        when(repository.saveAndFlush(any(Agenda.class))).thenAnswer(chamada -> chamada.getArgument(0));

        service.cadastrar(form(MEDICO_ID, AMANHA, LocalTime.of(8, 30), 30));
        service.cadastrar(form(MEDICO_ID, AMANHA, LocalTime.of(10, 0), 30));
        verify(repository, times(2)).saveAndFlush(any());
    }

    // ---------- Consulta de horários ----------

    @Test
    void listaApenasHorariosDisponiveisAplicandoOsFiltros() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Agenda> pagina = new PageImpl<>(List.of(new Agenda(new Medico(), AMANHA, NOVE_HORAS)));
        when(repository.buscarFuturos(eq(StatusAgenda.DISPONIVEL), eq(HOJE), eq(AGORA.toLocalTime()),
                eq(MEDICO_ID), eq(AMANHA), isNull(), eq(pageable))).thenReturn(pagina);

        AgendaFiltro filtro = new AgendaFiltro();
        filtro.setMedicoId(MEDICO_ID);
        filtro.setData(AMANHA);
        filtro.setEspecialidade("  "); // em branco = sem filtro

        assertThat(service.listarDisponiveis(filtro, pageable)).isSameAs(pagina);
    }

    @Test
    void horarioReservadoOuPassadoNaoServeParaATelaDeConfirmacao() {
        Agenda reservado = new Agenda(new Medico(), AMANHA, NOVE_HORAS);
        reservado.reservar();
        Agenda passado = new Agenda(new Medico(), HOJE.minusDays(1), NOVE_HORAS);
        when(repository.findComMedicoById(1L)).thenReturn(Optional.of(reservado));
        when(repository.findComMedicoById(2L)).thenReturn(Optional.of(passado));
        when(repository.findComMedicoById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarDisponivel(1L))
                .isInstanceOf(RegraNegocioException.class).hasMessage(AgendaService.HORARIO_INDISPONIVEL);
        assertThatThrownBy(() -> service.buscarDisponivel(2L))
                .isInstanceOf(RegraNegocioException.class).hasMessage(AgendaService.HORARIO_INDISPONIVEL);
        assertThatThrownBy(() -> service.buscarDisponivel(3L)).isInstanceOf(RegistroNaoEncontradoException.class);
    }

    // ---------- Geração em lote ----------

    @Test
    void loteGeraUmHorarioPorIntervaloSoNosDiasEscolhidosEPulaOsQueJaExistem() {
        Medico medico = medicoExistente();
        // Segunda 14/01 a domingo 20/01, só segundas e quartas, das 8h às 10h, a cada 30 minutos.
        AgendaLoteForm form = lote(LocalDate.of(2030, 1, 14), LocalDate.of(2030, 1, 20),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), "08:00", "10:00", 30);
        // 08:30 de segunda já está na agenda.
        when(repository.findByMedicoIdAndDataBetween(MEDICO_ID, form.getDataInicio(), form.getDataFim()))
                .thenReturn(List.of(new Agenda(medico, LocalDate.of(2030, 1, 14), LocalTime.of(8, 30))));

        AgendaService.ResultadoLote resultado = service.gerarEmLote(form);

        // 4 horários por dia (8:00, 8:30, 9:00, 9:30; o das 10h terminaria depois do fim) x 2 dias, menos 1 repetido.
        assertThat(resultado.gerados()).isEqualTo(7);
        assertThat(resultado.ignorados()).isEqualTo(1);

        List<Agenda> gravados = gravadosNoLote();
        assertThat(gravados).hasSize(7)
                .allSatisfy(agenda -> {
                    assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
                    assertThat(agenda.getMedico()).isSameAs(medico);
                    assertThat(agenda.getData().getDayOfWeek()).isIn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
                });
        assertThat(gravados).extracting(Agenda::getDataHora)
                .doesNotContain(LocalDateTime.of(2030, 1, 14, 8, 30))
                .contains(LocalDateTime.of(2030, 1, 16, 9, 30));
    }

    @Test
    void lotePulaOsHorariosDeHojeQueJaPassaram() {
        medicoExistente();
        // Hoje (quinta), das 8h às 12h: às 10h do relógio, 8:00 a 10:00 já passaram.
        AgendaLoteForm form = lote(HOJE, HOJE, EnumSet.of(DayOfWeek.THURSDAY), "08:00", "12:00", 30);
        when(repository.findByMedicoIdAndDataBetween(any(), any(), any())).thenReturn(List.of());

        AgendaService.ResultadoLote resultado = service.gerarEmLote(form);

        assertThat(resultado.gerados()).isEqualTo(3); // 10:30, 11:00, 11:30
        assertThat(resultado.ignorados()).isEqualTo(5);
        assertThat(gravadosNoLote()).extracting(Agenda::getHorario)
                .containsExactly(LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30));
    }

    @Test
    void loteComDadosInconsistentesERejeitadoNoCampoCorretoSemGravar() {
        when(medicoService.existe(MEDICO_ID)).thenReturn(true);

        assertCampoInvalido("dataFim", form -> form.setDataFim(form.getDataInicio().minusDays(1)));
        assertCampoInvalido("dataFim", form -> form.setDataFim(form.getDataInicio().plusDays(AgendaService.PERIODO_MAXIMO_DIAS)));
        assertCampoInvalido("dataInicio", form -> form.setDataInicio(HOJE.minusDays(1)));
        assertCampoInvalido("diasSemana", form -> form.setDiasSemana(EnumSet.noneOf(DayOfWeek.class)));
        assertCampoInvalido("horaFim", form -> form.setHoraFim(form.getHoraInicio()));
        assertCampoInvalido("horaFim", form -> form.setHoraFim(form.getHoraInicio().plusMinutes(20)));
        assertCampoInvalido("duracaoMinutos", form -> form.setDuracaoMinutos(5));

        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void loteSemNenhumHorarioNovoOuGrandeDemaisERejeitado() {
        Medico medico = medicoExistente();
        AgendaLoteForm repetido = lote(AMANHA, AMANHA, EnumSet.of(AMANHA.getDayOfWeek()), "09:00", "09:30", 30);
        when(repository.findByMedicoIdAndDataBetween(MEDICO_ID, AMANHA, AMANHA))
                .thenReturn(List.of(new Agenda(medico, AMANHA, NOVE_HORAS)));

        assertThatThrownBy(() -> service.gerarEmLote(repetido))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageStartingWith("Nenhum horário novo para gerar");

        // 92 dias, todos os dias, o dia inteiro a cada 10 minutos: bem acima do limite.
        AgendaLoteForm enorme = lote(AMANHA, AMANHA.plusDays(AgendaService.PERIODO_MAXIMO_DIAS - 1),
                EnumSet.allOf(DayOfWeek.class), "00:00", "23:50", 10);
        when(repository.findByMedicoIdAndDataBetween(MEDICO_ID, enorme.getDataInicio(), enorme.getDataFim()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.gerarEmLote(enorme))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("o máximo é " + AgendaService.MAXIMO_HORARIOS_LOTE);
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void loteBarradoPelaRestricaoUnicaNaoGravaNadaEPedeParaTentarDeNovo() {
        medicoExistente();
        when(repository.findByMedicoIdAndDataBetween(any(), any(), any())).thenReturn(List.of());
        when(repository.saveAllAndFlush(anyList())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.gerarEmLote(loteValido()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A agenda deste médico foi alterada por outra pessoa ao mesmo tempo. Tente novamente.");
    }

    @Test
    void loteIgnoraHorariosParcialmenteSobrepostosEGravaADuracao() {
        Medico medico = medicoExistente();
        // 09:15 às 10:15 invade os horários das 09:00 e das 10:00; o das 11:00 fica livre.
        when(repository.findByMedicoIdAndDataBetween(MEDICO_ID, AMANHA, AMANHA))
                .thenReturn(List.of(new Agenda(medico, AMANHA, LocalTime.of(9, 15), 60)));

        AgendaService.ResultadoLote resultado = service.gerarEmLote(
                lote(AMANHA, AMANHA, EnumSet.of(AMANHA.getDayOfWeek()), "09:00", "12:00", 60));

        assertThat(resultado).isEqualTo(new AgendaService.ResultadoLote(1, 2));
        assertThat(gravadosNoLote()).singleElement().satisfies(agenda -> {
            assertThat(agenda.getHorario()).isEqualTo(LocalTime.of(11, 0));
            assertThat(agenda.getDuracaoMinutos()).isEqualTo(60);
        });
        verify(medicoService).bloquear(MEDICO_ID);
    }

    // ---------- Apoio ----------

    private void assertCampoInvalido(String campo, Consumer<AgendaLoteForm> alteracao) {
        AgendaLoteForm form = loteValido();
        alteracao.accept(form);

        assertThatThrownBy(() -> service.gerarEmLote(form))
                .isInstanceOf(RegraNegocioException.class)
                .extracting(e -> ((RegraNegocioException) e).getCampo()).isEqualTo(campo);
    }

    @SuppressWarnings("unchecked")
    private List<Agenda> gravadosNoLote() {
        ArgumentCaptor<List<Agenda>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    private void assertSobreposto(String horario, int duracao) {
        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, AMANHA, LocalTime.parse(horario), duracao)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(AgendaService.HORARIO_SOBREPOSTO)
                .extracting(e -> ((RegraNegocioException) e).getCampo()).isEqualTo("horario");
    }

    private static AgendaForm form(Long medicoId, LocalDate data, LocalTime horario, Integer duracao) {
        AgendaForm form = form(medicoId, data, horario);
        form.setDuracaoMinutos(duracao);
        return form;
    }

    private Medico medicoExistente() {
        Medico medico = new Medico();
        medico.setId(MEDICO_ID);
        when(medicoService.existe(MEDICO_ID)).thenReturn(true);
        when(medicoService.buscar(MEDICO_ID)).thenReturn(medico);
        return medico;
    }

    private static AgendaForm form(Long medicoId, LocalDate data, LocalTime horario) {
        AgendaForm form = new AgendaForm();
        form.setMedicoId(medicoId);
        form.setData(data);
        form.setHorario(horario);
        return form;
    }

    private static AgendaLoteForm loteValido() {
        return lote(AMANHA, AMANHA.plusDays(6), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "08:00", "12:00", 30);
    }

    private static AgendaLoteForm lote(LocalDate inicio, LocalDate fim, EnumSet<DayOfWeek> dias,
                                       String horaInicio, String horaFim, int duracao) {
        AgendaLoteForm form = new AgendaLoteForm();
        form.setMedicoId(MEDICO_ID);
        form.setDataInicio(inicio);
        form.setDataFim(fim);
        form.setDiasSemana(dias);
        form.setHoraInicio(LocalTime.parse(horaInicio));
        form.setHoraFim(LocalTime.parse(horaFim));
        form.setDuracaoMinutos(duracao);
        return form;
    }
}
