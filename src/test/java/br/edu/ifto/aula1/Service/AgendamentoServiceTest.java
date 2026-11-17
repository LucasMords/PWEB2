package br.edu.ifto.aula1.Service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regras de agendamento, cancelamento e confirmação de presença, com os repositórios simulados. */
@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    private static final Long AGENDA_ID = 10L;
    private static final Long AGENDAMENTO_ID = 20L;
    private static final Long PACIENTE_ID = 1L;

    @Mock private AgendamentoRepository repository;
    @Mock private AgendaRepository agendaRepository;
    @Mock private ConsultaRepository consultaRepository;
    @Mock private PacienteService pacienteService;

    private AgendamentoService service;
    private Paciente paciente;
    private Agenda agenda;

    @BeforeEach
    void preparar() {
        service = new AgendamentoService(repository, agendaRepository, consultaRepository, pacienteService);

        paciente = new Paciente();
        paciente.setId(PACIENTE_ID);
        agenda = new Agenda(new Medico(), LocalDate.now().plusDays(1), LocalTime.of(9, 0));

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
        assertThat(agendamento.getDataCriacao()).isNotNull();
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
        Agenda passada = new Agenda(new Medico(), LocalDate.now().minusDays(1), LocalTime.of(9, 0));
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
        Agenda outra = new Agenda(new Medico(), LocalDate.now().plusDays(2), LocalTime.of(9, 0));
        when(agendaRepository.buscarComBloqueio(11L)).thenReturn(Optional.of(outra));
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

        Consulta consulta = service.confirmarPresenca(AGENDAMENTO_ID);

        assertThat(consulta.getAgendamento()).isSameAs(agendamento);
        assertThat(consulta.getDataRealizacao()).isNotNull();
        assertThat(consulta.getPaciente()).isSameAs(paciente);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.REALIZADO);
        // O horário continua ocupado: uma consulta realizada não devolve a vaga.
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);
        verify(consultaRepository).saveAndFlush(consulta);
    }

    @Test
    void segundaConfirmacaoERejeitadaSemCriarOutraConsulta() {
        agendamentoExistente();
        service.confirmarPresenca(AGENDAMENTO_ID);

        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A presença deste agendamento já foi confirmada.");
        verify(consultaRepository).saveAndFlush(any()); // uma única vez, a da primeira confirmação
    }

    @Test
    void naoCriaConsultaSeJaExisteUmaParaOAgendamento() {
        agendamentoExistente();
        when(consultaRepository.existsByAgendamentoId(any())).thenReturn(true);

        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A presença deste agendamento já foi confirmada.");
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoConfirmaPresencaDeAgendamentoCancelado() {
        Agendamento agendamento = agendamentoExistente();
        service.cancelar(AGENDAMENTO_ID);

        assertThatThrownBy(() -> service.confirmarPresenca(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível confirmar a presença em um agendamento cancelado.");
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        verify(consultaRepository, never()).saveAndFlush(any());
    }

    // ---------- Cancelamento ----------

    @Test
    void cancelamentoMantemORegistroELiberaOHorario() {
        Agendamento agendamento = agendamentoExistente();

        service.cancelar(AGENDAMENTO_ID);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.DISPONIVEL);
        verify(repository, never()).delete(any());
    }

    @Test
    void horarioCanceladoPodeSerAgendadoDeNovoComUmNovoRegistro() {
        Agendamento cancelado = agendamentoExistente();
        service.cancelar(AGENDAMENTO_ID);

        Agendamento novo = service.agendar(AGENDA_ID, PACIENTE_ID);

        assertThat(novo).isNotSameAs(cancelado);
        assertThat(novo.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(cancelado.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);
    }

    @Test
    void naoCancelaAgendamentoRealizadoNemCanceladoDuasVezes() {
        Agendamento realizado = agendamentoExistente();
        service.confirmarPresenca(AGENDAMENTO_ID);

        assertThatThrownBy(() -> service.cancelar(AGENDAMENTO_ID))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Não é possível cancelar um agendamento já realizado.");
        assertThat(realizado.getStatus()).isEqualTo(StatusAgendamento.REALIZADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);

        Agenda outraAgenda = new Agenda(new Medico(), LocalDate.now().plusDays(3), LocalTime.of(9, 0));
        outraAgenda.reservar();
        when(repository.buscarComBloqueio(21L))
                .thenReturn(Optional.of(new Agendamento(outraAgenda, paciente, LocalDateTime.now())));
        service.cancelar(21L);

        assertThatThrownBy(() -> service.cancelar(21L))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Este agendamento já foi cancelado.");
    }

    @Test
    void pacienteSoCancelaOProprioAgendamento() {
        Agendamento agendamento = agendamentoExistente();

        assertThatThrownBy(() -> service.cancelarDoPaciente(AGENDAMENTO_ID, 2L))
                .isInstanceOf(RegistroNaoEncontradoException.class);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(agenda.getStatus()).isEqualTo(StatusAgenda.AGENDADO);

        service.cancelarDoPaciente(AGENDAMENTO_ID, PACIENTE_ID);
        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
    }

    @Test
    void agendamentoInexistenteNaoPodeSerCanceladoNemConfirmado() {
        when(repository.buscarComBloqueio(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(99L)).isInstanceOf(RegistroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.confirmarPresenca(99L)).isInstanceOf(RegistroNaoEncontradoException.class);
        assertThatThrownBy(() -> service.confirmarPresenca(null)).isInstanceOf(RegistroNaoEncontradoException.class);
    }

    /** Um agendamento AGENDADO do paciente no horário de teste, encontrado pelo repositório. */
    private Agendamento agendamentoExistente() {
        agenda.reservar();
        Agendamento agendamento = new Agendamento(agenda, paciente, LocalDateTime.now());
        when(repository.buscarComBloqueio(AGENDAMENTO_ID)).thenReturn(Optional.of(agendamento));
        return agendamento;
    }
}
