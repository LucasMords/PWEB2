package br.edu.ifto.aula1;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Aula1ApplicationFusoTest {

    private final TimeZone fusoOriginal = TimeZone.getDefault();

    @AfterEach
    void restauraOFusoDaJvm() {
        TimeZone.setDefault(fusoOriginal);
    }

    @Test
    void semConfiguracaoValeOFusoDoTocantinsEUmInvalidoERecusado() {
        assertThat(Aula1Application.fuso(null)).isEqualTo(ZoneId.of("America/Araguaina"));
        assertThat(Aula1Application.fuso(" ")).isEqualTo(ZoneId.of("America/Araguaina"));
        assertThat(Aula1Application.fuso("America/Manaus")).isEqualTo(ZoneId.of("America/Manaus"));
        assertThatThrownBy(() -> Aula1Application.fuso("Nao/Existe")).isInstanceOf(DateTimeException.class);
    }

    @Test
    void oFusoJaValeQuandoOSpringSobeEmMaquinaComOutroFuso() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        ZoneId esperado = Aula1Application.fuso(System.getenv("CLINICA_FUSO"));
        String[] argumentos = {};

        try (MockedStatic<SpringApplication> spring = Mockito.mockStatic(SpringApplication.class)) {
            spring.when(() -> SpringApplication.run(Aula1Application.class, argumentos)).then(chamada -> {
                assertThat(ZoneId.systemDefault()).isEqualTo(esperado);
                return null;
            });

            Aula1Application.main(argumentos);

            spring.verify(() -> SpringApplication.run(Aula1Application.class, argumentos));
        }
    }

    @Test
    void fusoInvalidoNaVariavelDeAmbienteImpedeASubida() throws Exception {
        // Processo à parte: a variável de ambiente só pode ser definida antes de a JVM iniciar.
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        ProcessBuilder comando = new ProcessBuilder(java.toString(), Aula1Application.class.getName());
        comando.environment().put("CLASSPATH", System.getProperty("java.class.path"));
        comando.environment().put("CLINICA_FUSO", "Nao/Existe");
        Path saida = Files.createTempFile("fuso", ".log");
        try {
            Process processo = comando.redirectErrorStream(true).redirectOutput(saida.toFile()).start();
            try {
                assertThat(processo.waitFor(60, TimeUnit.SECONDS)).isTrue();
            } finally {
                processo.destroyForcibly().waitFor();
            }
            assertThat(processo.exitValue()).isNotZero();
            assertThat(saida).content().contains("Nao/Existe").contains("ZoneRulesException");
        } finally {
            Files.deleteIfExists(saida);
        }
    }
}
