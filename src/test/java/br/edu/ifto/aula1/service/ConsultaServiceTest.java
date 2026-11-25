package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Consulta;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.model.repository.ConsultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaServiceTest {

    @Mock
    private ConsultaRepository repository;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private MedicoService medicoService;
    @InjectMocks
    private ConsultaService service;

    private final Consulta consulta = mock(Consulta.class);

    @BeforeEach
    void consultaDoMedico1() {
        Medico medico = medico(1L);
        when(consulta.getMedico()).thenReturn(medico);
        when(repository.findDetalhadaById(10L)).thenReturn(Optional.of(consulta));
    }

    @Test
    void medicoQueAtendeuAlteraEAAuditoriaNaoGuardaOTexto() {
        Medico medico = medico(1L);
        when(medicoService.buscarPorLogin("medico")).thenReturn(Optional.of(medico));
        when(consulta.getId()).thenReturn(10L);

        service.atualizarObservacoes(10L, " Retorno em 30 dias. ", "medico");

        verify(consulta).setObservacoes("Retorno em 30 dias.");
        verify(auditoria).registrar(AcaoAuditoria.CONSULTA_ALTERADA, "Consulta", 10L, null);
    }

    @Test
    void outroMedicoNaoAltera() {
        Medico outro = medico(2L);
        when(medicoService.buscarPorLogin("outro")).thenReturn(Optional.of(outro));
        assertNegado("outro");
    }

    @Test
    void contaSemMedicoNaoAltera() {
        when(medicoService.buscarPorLogin("atendente")).thenReturn(Optional.empty());
        assertNegado("atendente");
    }

    private void assertNegado(String login) {
        assertThatThrownBy(() -> service.atualizarObservacoes(10L, "Outra coisa.", login))
                .isInstanceOf(AccessDeniedException.class);
        verify(consulta, never()).setObservacoes(any());
        verifyNoInteractions(auditoria);
    }

    private static Medico medico(Long id) {
        Medico medico = mock(Medico.class);
        when(medico.getId()).thenReturn(id);
        return medico;
    }
}
