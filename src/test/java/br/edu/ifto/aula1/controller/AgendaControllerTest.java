package br.edu.ifto.aula1.controller;

import br.edu.ifto.aula1.dto.AgendaLoteForm;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.service.AgendaDoDiaService;
import br.edu.ifto.aula1.service.AgendaService;
import br.edu.ifto.aula1.service.MedicoService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ModelMap;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgendaControllerTest {

    @Test
    void geracaoEmLoteIniciaComAsDatasDoRelogioNoFusoDaClinica() {
        // 02h30 de 1º de março em UTC ainda é 28 de fevereiro no Tocantins.
        Clock clock = Clock.fixed(Instant.parse("2030-03-01T02:30:00Z"), ZoneId.of("America/Araguaina"));
        MedicoService medicos = mock(MedicoService.class);
        when(medicos.buscarPorLogin("medico")).thenReturn(Optional.of(new Medico()));
        AgendaController controller = new AgendaController(
                mock(AgendaService.class), medicos, mock(AgendaDoDiaService.class), clock);
        ModelMap model = new ModelMap();

        controller.lote(new TestingAuthenticationToken("medico", "senha"), model);

        AgendaLoteForm form = (AgendaLoteForm) model.get("form");
        assertThat(form.getDataInicio()).isEqualTo(LocalDate.of(2030, 2, 28));
        assertThat(form.getDataFim()).isEqualTo(LocalDate.of(2030, 3, 28));
    }
}
