package br.edu.ifto.aula1;

import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
class ConcorrenciaTests {
    @Autowired WebApplicationContext context;
    @Autowired ConsultaRepository consultas;

    @Test void doisAgendamentosSimultaneosReservamUmUnicoHorario() throws Exception {
        LocalDateTime horario = LocalDateTime.now().plusYears(2).withSecond(0).withNano(0);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        CountDownLatch inicio = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> agendar = () -> {
                inicio.await();
                int status = mvc.perform(post("/consultas/save")
                                .param("paciente.id", "1").param("medico.id", "1")
                                .param("dataHora", horario.toString()).param("status", "AGENDADA"))
                        .andReturn().getResponse().getStatus();
                assertThat(status).isIn(200, 302);
                return status == 302;
            };
            Future<Boolean> primeiro = executor.submit(agendar);
            Future<Boolean> segundo = executor.submit(agendar);
            inicio.countDown();
            assertThat(java.util.List.of(primeiro.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(consultas.findAll().stream().filter(c -> horario.equals(c.getDataHora())).count()).isEqualTo(1);
        } finally {
            consultas.findAll().stream().filter(c -> horario.equals(c.getDataHora()))
                    .forEach(c -> consultas.deleteById(c.getId()));
        }
    }
}
