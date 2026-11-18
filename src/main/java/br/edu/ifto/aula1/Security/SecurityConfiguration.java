package br.edu.ifto.aula1.Security;

import br.edu.ifto.aula1.Model.Entity.Perfil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;


/**
 * Quem acessa o quê:
 * - visitante: página de login, cadastro de paciente e a verificação de saúde (/actuator/health);
 * - PACIENTE: início e os próprios dados, agendamentos e consultas, só para leitura;
 * - MEDICO: cadastra os horários disponíveis da própria agenda;
 * - ATENDENTE: marca, cancela e reagenda consultas, confirma presença e registra falta;
 * - ADMIN, ATENDENTE e MEDICO: pacientes, consultas, a lista de médicos e a agenda;
 * - ADMIN: cadastro de médicos, exclusões, auditoria e o console H2 (a conta admin
 *   também tem o perfil ATENDENTE).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // As regras são avaliadas na ordem em que aparecem; anyRequest() fica por último.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/error").permitAll()
                        // Verificação de saúde (Docker, balanceador): pública, só responde UP ou DOWN.
                        // Qualquer outro endpoint do Actuator, se for exposto um dia, exige ADMIN.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole(Perfil.ADMIN)
                        // Política de privacidade (LGPD): pública, é lida antes do cadastro.
                        .requestMatchers(HttpMethod.GET, "/privacidade").permitAll()
                        // Registro de auditoria: só a administração.
                        .requestMatchers("/auditoria/**").hasRole(Perfil.ADMIN)

                        // Público: o paciente cria o próprio cadastro e a conta de acesso.
                        .requestMatchers(HttpMethod.GET, "/pacientes/form").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pacientes/save").permitAll()

                        // Somente ADMIN gerencia médicos (e cria a conta institucional deles).
                        .requestMatchers("/medicos/form", "/medicos/edit/**").hasRole(Perfil.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/medicos/save", "/medicos/update").hasRole(Perfil.ADMIN)
                        // Exclusões também exigem ADMIN (e são POST, para passarem pela proteção CSRF).
                        .requestMatchers("/medicos/remove/**", "/pacientes/remove/**").hasRole(Perfil.ADMIN)
                        // O console H2 também exige ADMIN.
                        .requestMatchers("/h2-console/**").hasRole(Perfil.ADMIN)

                        // Horários disponíveis: só o médico cadastra, e apenas na própria agenda
                        // (o AgendaController usa o médico da conta, não o do formulário).
                        .requestMatchers("/agenda/form", "/agenda/save", "/agenda/lote").hasRole(Perfil.MEDICO)
                        // Marcar, cancelar e reagendar, confirmar a presença (o que cria a Consulta) e
                        // registrar falta: o atendente, que informa os horários ao paciente e marca a
                        // consulta com o médico. A conta admin também tem o perfil ATENDENTE.
                        .requestMatchers("/agendamentos/novo/**", "/agendamentos/save", "/agendamentos/cancelar/**",
                                "/agendamentos/reagendar/**", "/agendamentos/confirmar/**", "/agendamentos/falta/**")
                                .hasRole(Perfil.ATENDENTE)
                        // Consulta da agenda (horários livres, agenda do dia, agendamentos), cadastros e
                        // histórico de consultas: a equipe da clínica. O paciente não vê dados de outros.
                        .requestMatchers("/agenda/**", "/agendamentos/**", "/pacientes/**", "/medicos/**", "/consultas/**")
                                .hasAnyRole(Perfil.ADMIN, Perfil.ATENDENTE, Perfil.MEDICO)

                        // O restante (início e /usuario/**: os dados, os agendamentos e as consultas de
                        // quem está logado, só para leitura) exige apenas estar autenticado.
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        // Sem o "true": quem foi barrado numa página volta para ela depois de entrar.
                        .defaultSuccessUrl("/")
                        .permitAll()
                )
                // Credenciais no cabeçalho Authorization, para ferramentas como o curl.
                // O navegador continua sendo redirecionado para a página de login.
                .httpBasic(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .rememberMe(Customizer.withDefaults())

                // O console H2 é uma aplicação à parte: não envia o token CSRF e abre em frames.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        // Não envia o endereço completo (que pode ter ids e datas) para outros sites.
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        // CSP: só scripts e estilos da própria aplicação e do CDN do Bootstrap; nada
                        // inline. O console H2 usa scripts inline e fica de fora.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**")),
                                new StaticHeadersWriter("Content-Security-Policy", POLITICA_DE_CONTEUDO))));
        // O HSTS (Strict-Transport-Security) já vem ligado e é enviado nas respostas por HTTPS.

        return http.build();
    }

    static final String POLITICA_DE_CONTEUDO = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://cdn.jsdelivr.net",
            "style-src 'self' https://cdn.jsdelivr.net",
            "img-src 'self' data:",
            "font-src 'self' https://cdn.jsdelivr.net",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'self'");

    /**
     * Publica os eventos de autenticação (senha errada, login feito), que o
     * EventosDeAutenticacao usa para o bloqueio por tentativas e para a auditoria.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
        return new DefaultAuthenticationEventPublisher(publisher);
    }

    // As senhas são armazenadas como hash BCrypt, nunca em texto puro.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
