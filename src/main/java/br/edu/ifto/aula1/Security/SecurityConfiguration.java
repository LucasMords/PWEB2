package br.edu.ifto.aula1.Security;

import br.edu.ifto.aula1.Model.Entity.Perfil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;


/**
 * Quem acessa o quê:
 * - visitante: página de login e cadastro de paciente;
 * - PACIENTE: início, os próprios dados, agendamentos e consultas, e os horários livres;
 * - USER (recepção) e MEDICO: pacientes, consultas, a lista de médicos e a agenda;
 * - USER e ADMIN: confirmação de presença e registro de falta dos agendamentos;
 * - ADMIN: tudo, incluindo cadastro de médicos, exclusões e o console H2.
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

                        // Agenda: qualquer usuário autenticado vê os horários livres, agenda e
                        // cancela. O paciente só age em nome próprio (conferido nos controllers e
                        // no AgendamentoService); a equipe escolhe o paciente.
                        .requestMatchers(HttpMethod.GET, "/agenda/list", "/agendamentos/novo/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/agendamentos/save", "/agendamentos/cancelar/**").authenticated()
                        // Confirmar a presença (o que cria a Consulta) e registrar falta é só da
                        // administração e da recepção.
                        .requestMatchers("/agendamentos/confirmar/**", "/agendamentos/falta/**")
                                .hasAnyRole(Perfil.ADMIN, Perfil.USER)
                        // O restante da agenda (cadastro de horários, gerenciamento) é da equipe.
                        .requestMatchers("/agenda/**", "/agendamentos/**")
                                .hasAnyRole(Perfil.ADMIN, Perfil.USER, Perfil.MEDICO)

                        // Cadastros e histórico de consultas são da equipe da clínica. Quem tem só o perfil PACIENTE
                        // não vê os dados dos outros pacientes.
                        .requestMatchers("/pacientes/**", "/medicos/**", "/consultas/**")
                                .hasAnyRole(Perfil.ADMIN, Perfil.USER, Perfil.MEDICO)

                        // O restante (início e /usuario/**, com os dados e as consultas de quem
                        // está logado) exige apenas estar autenticado.
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
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    // As senhas são armazenadas como hash BCrypt, nunca em texto puro.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
