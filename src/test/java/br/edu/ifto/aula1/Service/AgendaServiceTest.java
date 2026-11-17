package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Dto.AgendaFiltro;
import br.edu.ifto.aula1.Dto.AgendaForm;
import br.edu.ifto.aula1.Model.Entity.Agenda;
import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.Entity.StatusAgenda;
import br.edu.ifto.aula1.Model.repository.AgendaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras do cadastro e da consulta de horários, com os repositórios simulados. */
@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    private static final Long MEDICO_ID = 7L;
    private static final LocalDate AMANHA = LocalDate.now().plusDays(1);
    private static final LocalTime NOVE_HORAS = LocalTime.of(9, 0);

    @Mock private AgendaRepository repository;
    @Mock private MedicoService medicoService;
    @InjectMocks private AgendaService service;

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

        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, LocalDate.now().minusDays(1), NOVE_HORAS)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("A data não pode estar no passado.");
        assertThatThrownBy(() -> service.cadastrar(form(MEDICO_ID, LocalDate.now(), LocalTime.MIDNIGHT)))
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
    void listaApenasHorariosDisponiveisAplicandoOsFiltros() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Agenda> pagina = new PageImpl<>(List.of(new Agenda(new Medico(), AMANHA, NOVE_HORAS)));
        when(repository.buscarFuturos(eq(StatusAgenda.DISPONIVEL), eq(LocalDate.now()), any(LocalTime.class),
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
        Agenda passado = new Agenda(new Medico(), LocalDate.now().minusDays(1), NOVE_HORAS);
        when(repository.findComMedicoById(1L)).thenReturn(Optional.of(reservado));
        when(repository.findComMedicoById(2L)).thenReturn(Optional.of(passado));
        when(repository.findComMedicoById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarDisponivel(1L))
                .isInstanceOf(RegraNegocioException.class).hasMessage(AgendaService.HORARIO_INDISPONIVEL);
        assertThatThrownBy(() -> service.buscarDisponivel(2L))
                .isInstanceOf(RegraNegocioException.class).hasMessage(AgendaService.HORARIO_INDISPONIVEL);
        assertThatThrownBy(() -> service.buscarDisponivel(3L)).isInstanceOf(RegistroNaoEncontradoException.class);
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
}
