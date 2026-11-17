package br.edu.ifto.aula1.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Relógio usado pelas regras de horário (horário no passado, janela de confirmação de
 * presença, registro de falta). Os serviços leem a hora por ele, e não por
 * LocalDateTime.now() direto, para que os testes possam fixar o "agora".
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
