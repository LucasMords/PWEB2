package br.edu.ifto.aula1.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regras de agendamento, cancelamento, confirmação de presença e falta, com os
 * repositórios simulados. O relógio é fixo e pode ser "adiantado" com {@link #relogioEm}.
 */
@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    private static final Long AGENDA_ID = 10L;
    private static final Long AGENDAMENTO_ID = 20L;
    private static final Long PACIENTE_ID = 1L;
    private static final String RECEPCAO = "atendente";
    private static final ZoneId FUSO = ZoneId.systemDefault();

    // Hoje é 10/01/2030, 10h; a consulta de teste é depois de amanhã às 9h (fora do prazo de 24h).
    private static final LocalDateTime AGORA = LocalDateTime.of(2030, 1, 10, 10, 0);
    private static final LocalDateTime HORARIO_CONSULTA = LocalDateTime.of(2030, 1, 12, 9, 0);

    @Mock private AgendamentoRepository repository;
    @Mock private AgendaRepository agendaRepository;
    @Mock private ConsultaRepository consultaRepository;
    @Mock private PacienteService pacienteService;
    @Mock private AuditoriaService auditoria;

    private AgendamentoService service;
    private Paciente paciente;
    private Agenda agenda;

    @BeforeEach
    void preparar() {
        relogioEm(AGORA);

        paciente = new Paciente();
        paciente.setId(PACIENTE_ID);
        agenda = horario(HORARIO_CONSULTA);

        lenient().when(pacienteService.buscar(PACIENTE_ID)).thenReturn(paciente);
        lenient().when(agendaRepository.buscarComBloqueio(AGENDA_ID)).thenReturn(Optional.of(agenda));
        lenient().when(repository.saveAndFlush(any(Agendamento.class))).thenAnswer(chamada -> chamada.getArgument(0));
        lenient().when(consultaRepository.saveAndFlush(any(Consulta.class))).thenAnswer(chamada -> chamada.getArgument(0));
    }

    // ---------- Agendamento ----------

    @Test
    void agendaHorarioDisponivelSemCriarConsulta() {
        Agendamento agendamento = service.agendar(AGENDA_ID, PACIENTE_ID);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(agendamento.getPaciente()).isSameAs(paciente);
        assertThat(agendamento.getAgenda()).isSameAs(agenda);
        assertThat(agendamento.getDataCriacao()).isEqualTo(AGORA);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);

        // A disponibilidade foi lida com bloqueio, e nenhuma consulta nasce no agendamento.
        verify(agendaRepository).buscarComBloqueio(AGENDA_ID);
        verifyNoInteractions(consultaRepository);
    }

    @Test
    void naoAgendaHorarioJaOcupado() {
        agenda.reservar();

        assertThatThrownBy(() -> service.agendar(AGENDA_ID, PACIENTE_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(AgendaService.HORARIO_INDISPONIVEL);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void naoAgendaSeJaExisteAgendamentoAtivoMesmoComAAgendaMarcadaComoLivre() {
        when(repository.existsByAgendaIdAndStatusNot(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.agendar(AGENDA_ID, PACIENTE_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(AgendaService.HORARIO_INDISPONIVEL);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void naoAgendaHorarioPassado() {
        Agenda passada = horario(AGORA.minusDays(1));
        when(agendaRepository.buscarComBloqueio(AGENDA_ID)).thenReturn(Optional.of(passada));

        assertThatThrownBy(() -> service.agendar(AGENDA_ID, PACIENTE_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível agendar um horário que já passou.");
        assertThat(passada.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void conflitosDeConcorrenciaViramHorarioIndisponivel() {
        // Tempo de espera do bloqueio esgotado.
        when(agendaRepository.buscarComBloqueio(AGENDA_ID)).thenThrow(new CannotAcquireLockException("timeout"));
        assertThatThrownBy(() -> service.agendar(AGENDA_ID, PACIENTE_ID))
                .isInstanceOf(RegraNegocioException.class).hasMessage(AgendaService.HORARIO_INDISPONIVEL);

        // Restrição única do banco (segunda barreira).
        when(agendaRepository.buscarComBloqueio(11L)).thenReturn(Optional.of(horario(HORARIO_CONSULTA.plusDays(1))));
        when(repository.saveAndFlush(any(Agendamento.class))).thenThrow(new DataIntegrityViolationException("uk"));
        assertThatThrownBy(() -> service.agendar(11L, PACIENTE_ID))
                .isInstanceOf(RegraNegocioException.class).hasMessage(AgendaService.HORARIO_INDISPONIVEL);
    }

    @Test
    void horarioOuPacienteInexistenteNaoGeraAgendamento() {
        when(agendaRepository.buscarComBloqueio(99L)).thenReturn(Optional.empty());
        when(pacienteService.buscar(99L)).thenThrow(new RegistroNaoEncontradoException("Paciente não encontrado."));

        assertThatThrownBy(() -> service.agendar(99L, PACIENTE_ID)).isInstanceOf(RegistroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.agendar(null, PACIENTE_ID)).isInstanceOf(RegistroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.agendar(AGENDA_ID, 99L)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    // ---------- Confirmação de presença ----------

    @Test
    void confirmacaoDePresencaCriaAConsultaEMarcaOAgendamentoComoRealizado() {
        Agendamento agendamento = agendamentoExistente();
        relogioEm(HORARIO_CONSULTA.plusMinutes(5));

        Consulta consulta = service.confirmarPresenca(AGENDAMENTO_ID);

        assertThat(consulta.getAgendamento()).isSameAs(agendamento);
        assertThat(consulta.getDataRealizacao()).isEqualTo(HORARIO_CONSULTA.plusMinutes(5));
        assertThat(consulta.getPaciente()).isSameAs(paciente);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.REALIZADO);
        // O horário continua ocupado: uma consulta realizada não devolve a vaga.
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);
        verify(consultaRepository).saveAndFlush(consulta);
    }

    @Test
    void presencaSoPodeSerConfirmadaAPartirDeUmaHoraAntesDoHorario() {
        Agendamento agendamento = agendamentoExistente();

        // Na véspera e um minuto antes da janela: rejeitado, sem consulta.
        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A presença só pode ser confirmada a partir de 1 hora antes do horário da consulta.");
        relogioEm(HORARIO_CONSULTA.minusMinutes(61));
        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID)).isInstanceOf(RegraNegocioException.class);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        verify(consultaRepository, never()).saveAndFlush(any());

        // Exatamente 1 hora antes: o paciente chegou cedo e já pode ser atendido.
        relogioEm(HORARIO_CONSULTA.minusHours(1));
        service.confirmarPresenca(AGENDAMENTO_ID);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.REALIZADO);
    }

    @Test
    void segundaConfirmacaoERejeitadaSemCriarOutraConsulta() {
        agendamentoExistente();
        relogioEm(HORARIO_CONSULTA);
        service.confirmarPresenca(AGENDAMENTO_ID);

        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A presença deste agendamento já foi confirmada.");
        verify(consultaRepository).saveAndFlush(any()); // uma única vez, a da primeira confirmação
    }

    @Test
    void naoCriaConsultaSeJaExisteUmaParaOAgendamento() {
        agendamentoExistente();
        relogioEm(HORARIO_CONSULTA);
        when(consultaRepository.existsByAgendamentoId(any())).thenReturn(true);

        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A presença deste agendamento já foi confirmada.");
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoConfirmaPresencaDeAgendamentoCancelado() {
        Agendamento agendamento = agendamentoExistente();
        service.cancelar(AGENDAMENTO_ID, RECEPCAO, null);
        relogioEm(HORARIO_CONSULTA);

        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível confirmar a presença em um agendamento cancelado.");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    // ---------- Falta ----------

    @Test
    void faltaDepoisDoHorarioMarcaFaltouSemConsultaEMantemOHorarioOcupado() {
        Agendamento agendamento = agendamentoExistente();
        relogioEm(HORARIO_CONSULTA.plusMinutes(30));

        service.registrarFalta(AGENDAMENTO_ID);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.FALTOU);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);
        verifyNoInteractions(consultaRepository);
    }

    @Test
    void faltaNaoPodeSerRegistradaAntesDoHorario() {
        Agendamento agendamento = agendamentoExistente();
        relogioEm(HORARIO_CONSULTA.minusMinutes(1));

        assertThatThrownBy(() -> service.registrarFalta(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A falta só pode ser registrada depois do horário da consulta.");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
    }

    @Test
    void agendamentoComFaltaNaoPodeSerConfirmadoCanceladoNemTerFaltaDeNovo() {
        Agendamento agendamento = agendamentoExistente();
        relogioEm(HORARIO_CONSULTA.plusHours(1));
        service.registrarFalta(AGENDAMENTO_ID);

        assertThatThrownBy(() -> service.registrarFalta(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class).hasMessage("A falta deste agendamento já foi registrada.");
        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class).hasMessage("A falta deste agendamento já foi registrada.");
        assertThatThrownBy(() -> service.cancelar(AGENDAMENTO_ID, RECEPCAO, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível cancelar um agendamento com falta registrada.");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.FALTOU);
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoRegistraFaltaEmAgendamentoRealizadoOuCancelado() {
        agendamentoExistente();
        relogioEm(HORARIO_CONSULTA);
        service.confirmarPresenca(AGENDAMENTO_ID);
        assertThatThrownBy(() -> service.registrarFalta(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class).hasMessage("A presença deste agendamento já foi confirmada.");

        Agendamento cancelado = new Agendamento(horario(HORARIO_CONSULTA.minusDays(1)), paciente, AGORA);
        cancelado.cancelar(AGORA, RECEPCAO, null);
        when(repository.buscarComBloqueio(21L)).thenReturn(Optional.of(cancelado));
        assertThatThrownBy(() -> service.registrarFalta(21L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível registrar falta em um agendamento cancelado.");
    }

    // ---------- Cancelamento ----------

    @Test
    void cancelamentoMantemORegistroELiberaOHorario() {
        Agendamento agendamento = agendamentoExistente();

        service.cancelar(AGENDAMENTO_ID, RECEPCAO, null);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
        verify(repository, never()).delete(any());
    }

    @Test
    void horarioCanceladoPodeSerAgendadoDeNovoComUmNovoRegistro() {
        Agendamento cancelado = agendamentoExistente();
        service.cancelar(AGENDAMENTO_ID, RECEPCAO, null);

        Agendamento novo = service.agendar(AGENDA_ID, PACIENTE_ID);

        assertThat(novo).isNotSameAs(cancelado);
        assertThat(novo.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(cancelado.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);
    }

    @Test
    void naoCancelaAgendamentoRealizadoNemCanceladoDuasVezes() {
        Agendamento realizado = agendamentoExistente();
        relogioEm(HORARIO_CONSULTA);
        service.confirmarPresenca(AGENDAMENTO_ID);

        assertThatThrownBy(() -> service.cancelar(AGENDAMENTO_ID, RECEPCAO, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível cancelar um agendamento já realizado.");
        assertThat(realizado.getStatus()).isEqualTo(StatusAgendamento.REALIZADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);

        Agenda outraAgenda = horario(HORARIO_CONSULTA.plusDays(2));
        outraAgenda.reservar();
        when(repository.buscarComBloqueio(21L))
                .thenReturn(Optional.of(new Agendamento(outraAgenda, paciente, AGORA)));
        service.cancelar(21L, RECEPCAO, null);

        assertThatThrownBy(() -> service.cancelar(21L, RECEPCAO, null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Este agendamento já foi cancelado.");
    }

    @Test
    void atendimentoCancelaMesmoDepoisDoHorario() {
        Agendamento agendamento = agendamentoExistente();
        relogioEm(HORARIO_CONSULTA.plusMinutes(5));

        // Ex.: o médico não pôde atender. Sem prazo mínimo para o atendimento.
        service.cancelar(AGENDAMENTO_ID, RECEPCAO, "Médico ausente.");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
    }

    @Test
    void cancelamentoGuardaQuemCancelouQuandoEOMotivo() {
        Agendamento agendamento = agendamentoExistente();

        service.cancelar(AGENDAMENTO_ID, RECEPCAO, "  Médico em congresso.  ");

        assertThat(agendamento.getMotivoCancelamento()).isEqualTo("Médico em congresso.");
        assertThat(agendamento.getCanceladoPor()).isEqualTo(RECEPCAO);
        assertThat(agendamento.getDataCancelamento()).isEqualTo(AGORA);
    }

    @Test
    void motivoEmBrancoFicaVazioEMotivoLongoDemaisERejeitadoSemCancelar() {
        Agendamento agendamento = agendamentoExistente();

        assertThatThrownBy(() -> service.cancelar(AGENDAMENTO_ID, RECEPCAO, "x".repeat(Agendamento.MOTIVO_MAXIMO + 1)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("O motivo deve ter no máximo 255 caracteres.");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);

        service.cancelar(AGENDAMENTO_ID, RECEPCAO, "   ");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getMotivoCancelamento()).isNull();
    }

    // ---------- Reagendamento ----------

    @Test
    void reagendamentoCancelaOAtualECriaUmNovoComOMesmoPaciente() {
        Agendamento atual = agendamentoExistente();
        Agenda nova = novoHorarioDoMesmoMedico(LocalDateTime.of(2030, 1, 13, 14, 0));

        Agendamento novo = service.reagendar(AGENDAMENTO_ID, 30L, RECEPCAO);

        assertThat(novo).isNotSameAs(atual);
        assertThat(novo.getAgenda()).isSameAs(nova);
        assertThat(novo.getPaciente()).isSameAs(paciente);
        assertThat(novo.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(nova.getStatus()).isEqualTo(StatusAgenda.AGENDADO);

        // O antigo fica no histórico, cancelado, dizendo para quando foi remarcado; o horário dele é liberado.
        assertThat(atual.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(atual.getMotivoCancelamento()).isEqualTo("Reagendado para 13/01/2030 às 14:00.");
        assertThat(atual.getCanceladoPor()).isEqualTo(RECEPCAO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
    }

    @Test
    void reagendamentoParaHorarioOcupadoOuDeOutroMedicoNaoMudaNada() {
        Agendamento atual = agendamentoExistente();
        Agenda ocupada = novoHorarioDoMesmoMedico(LocalDateTime.of(2030, 1, 13, 14, 0));
        ocupada.reservar();

        assertThatThrownBy(() -> service.reagendar(AGENDAMENTO_ID, 30L, RECEPCAO))
                .isInstanceOf(RegraNegocioException.class).hasMessage(AgendaService.HORARIO_INDISPONIVEL);

        Medico outroMedico = new Medico();
        outroMedico.setId(9L);
        when(agendaRepository.buscarComBloqueio(31L))
                .thenReturn(Optional.of(new Agenda(outroMedico, LocalDate.of(2030, 1, 13), LocalTime.of(15, 0))));
        assertThatThrownBy(() -> service.reagendar(AGENDAMENTO_ID, 31L, RECEPCAO))
                .isInstanceOf(RegraNegocioException.class).hasMessageStartingWith("O reagendamento deve ser com o mesmo médico.");

        assertThat(atual.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void naoReagendaAgendamentoComFaltaNemAbreATelaParaEle() {
        Agendamento agendamento = agendamentoExistente();
        relogioEm(HORARIO_CONSULTA.plusMinutes(10));
        service.registrarFalta(AGENDAMENTO_ID);

        assertThatThrownBy(() -> service.reagendar(AGENDAMENTO_ID, 30L, RECEPCAO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível reagendar um agendamento com falta registrada.");

        when(repository.findDetalhadoById(AGENDAMENTO_ID)).thenReturn(Optional.of(agendamento));
        assertThatThrownBy(() -> service.buscarParaReagendar(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível reagendar um agendamento com falta registrada.");
    }

    @Test
    void agendamentoInexistenteNaoPodeSerCanceladoConfirmadoNemTerFalta() {
        when(repository.buscarComBloqueio(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(99L, RECEPCAO, null)).isInstanceOf(RegistroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.confirmarPresenca(99L)).isInstanceOf(RegistroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.registrarFalta(99L)).isInstanceOf(RegistroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.confirmarPresenca(null)).isInstanceOf(RegistroNaoEncontradoException.class);
    }

    // ---------- Conflito de horário do paciente ----------

    @Test
    void naoAgendaSeOPacienteTemConsultaSobrepostaMesmoComOutroMedico() {
        // O horário de teste é das 09:00 às 09:30; este, de outro médico, vai das 08:45 às 09:15.
        pacienteOcupadoEm(HORARIO_CONSULTA.minusMinutes(15));

        assertThatThrownBy(() -> service.agendar(AGENDA_ID, PACIENTE_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(AgendamentoService.PACIENTE_OCUPADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
        verify(pacienteService).bloquear(PACIENTE_ID);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void agendaConsultaEncostadaEmOutraDoPaciente() {
        pacienteOcupadoEm(HORARIO_CONSULTA.minusMinutes(30));

        assertThat(service.agendar(AGENDA_ID, PACIENTE_ID).getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
    }

    @Test
    void reagendamentoParaCimaDeOutraConsultaDoPacienteNaoMudaNada() {
        Agendamento atual = agendamentoExistente();
        Agenda nova = novoHorarioDoMesmoMedico(LocalDateTime.of(2030, 1, 13, 14, 0));
        pacienteOcupadoEm(LocalDateTime.of(2030, 1, 13, 14, 15));

        assertThatThrownBy(() -> service.reagendar(AGENDAMENTO_ID, 30L, RECEPCAO))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage(AgendamentoService.PACIENTE_OCUPADO);
        assertThat(atual.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);
        assertThat(nova.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void oAgendamentoQueEstaSendoReagendadoNaoContaComoConflito() {
        Agendamento atual = agendamentoExistente();
        ReflectionTestUtils.setField(atual, "id", AGENDAMENTO_ID);
        // Novo horário 15 minutos depois: sobrepõe só o que será liberado.
        Agenda nova = novoHorarioDoMesmoMedico(HORARIO_CONSULTA.plusMinutes(15));
        when(repository.buscarAtivosDoPacienteNoDia(PACIENTE_ID, nova.getData(), StatusAgendamento.CANCELADO))
                .thenReturn(List.of(atual));

        assertThat(service.reagendar(AGENDAMENTO_ID, 30L, RECEPCAO).getAgenda()).isSameAs(nova);
        assertThat(atual.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    // ---------- Apoio ----------

    /** Recria o serviço com o relógio parado no instante informado (os mocks continuam os mesmos). */
    private void relogioEm(LocalDateTime instante) {
        Clock clock = Clock.fixed(instante.atZone(FUSO).toInstant(), FUSO);
        service = new AgendamentoService(repository, agendaRepository, consultaRepository, pacienteService, auditoria, clock);
    }

    /** Outro horário livre, do mesmo médico do horário de teste, encontrado com o id 30. */
    private Agenda novoHorarioDoMesmoMedico(LocalDateTime dataHora) {
        Agenda nova = new Agenda(agenda.getMedico(), dataHora.toLocalDate(), dataHora.toLocalTime());
        when(agendaRepository.buscarComBloqueio(30L)).thenReturn(Optional.of(nova));
        return nova;
    }

    /** O paciente já tem um agendamento ativo, com outro médico, neste instante. */
    private void pacienteOcupadoEm(LocalDateTime dataHora) {
        Agendamento outro = new Agendamento(horario(dataHora), paciente, AGORA);
        ReflectionTestUtils.setField(outro, "id", 99L);
        when(repository.buscarAtivosDoPacienteNoDia(PACIENTE_ID, dataHora.toLocalDate(), StatusAgendamento.CANCELADO))
                .thenReturn(List.of(outro));
    }

    private static Agenda horario(LocalDateTime dataHora) {
        return new Agenda(new Medico(), dataHora.toLocalDate(), dataHora.toLocalTime());
    }

    /** Um agendamento AGENDADO do paciente no horário de teste, encontrado pelo repositório. */
    private Agendamento agendamentoExistente() {
        agenda.reservar();
        Agendamento agendamento = new Agendamento(agenda, paciente, AGORA);
        when(repository.buscarComBloqueio(AGENDAMENTO_ID)).thenReturn(Optional.of(agendamento));
        return agendamento;
    }
}
