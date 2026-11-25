package br.edu.ifto.aula1;

import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.PerfilRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usa os usuários criados pelo InitialUsersInitializer: admin/Admin123 e atendente/Atendente123. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    private static final String USER = "atendente";
    private static final String SENHA_USER = "Atendente123";
    private static final String ADMIN = "admin";

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PerfilRepository perfis;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void paginaDeLoginEPublicaEExibeFormularioPersonalizado() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void autenticaUsuarioComSenhaBCryptERejeitaCredencialIncorretaOuInexistente() throws Exception {
        Usuario user = usuarios.findByLogin(USER).orElseThrow();
        assertThat(user.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches(SENHA_USER, user.getPassword())).isTrue();

        mvc.perform(post("/login").with(csrf()).param("username", USER).param("password", SENHA_USER))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(post("/login").with(csrf()).param("username", USER).param("password", "errada"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("username", "nao-existe").param("password", "qualquer"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @Transactional
    void usuarioInativoNaoAutentica() throws Exception {
        String login = "inativo-test";
        Usuario inativo = new Usuario(login, passwordEncoder.encode("Senha-teste-123!"), false);
        inativo.adicionarPerfil(perfis.findByNome("ATENDENTE").orElseThrow());
        usuarios.saveAndFlush(inativo);

        mvc.perform(post("/login").with(csrf()).param("username", login).param("password", "Senha-teste-123!"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void userAcessaAreasPermitidasMasNaoAsExclusivasDoAdmin() throws Exception {
        mvc.perform(get("/pacientes/list").with(user(USER).roles("ATENDENTE")))
                .andExpect(status().isOk());
        mvc.perform(get("/medicos/form").with(user(USER).roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/medicos/form").with(user(ADMIN).roles("ADMIN", "ATENDENTE")))
                .andExpect(status().isOk());
    }

    @Test
    void semAutenticacaoONavegadorVaiParaOLoginEOutrosClientesRecebem401() throws Exception {
        // O navegador pede HTML e é redirecionado para a página de login.
        mvc.perform(get("/medicos/form").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        // Um cliente como o curl recebe o desafio da autenticação básica.
        mvc.perform(get("/medicos/form"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")));
    }

    @Test
    void autenticacaoBasicaAceitaCredenciaisNoCabecalho() throws Exception {
        mvc.perform(get("/pacientes/list").with(httpBasic(USER, SENHA_USER)))
                .andExpect(status().isOk());
        mvc.perform(get("/pacientes/list").with(httpBasic(USER, "errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exclusaoExigePostComCsrfEPerfilAdmin() throws Exception {
        // O id não existe de propósito: o 404 mostra que a requisição passou pela segurança
        // e chegou ao controller, sem apagar os dados usados pelos outros testes.
        String url = "/pacientes/remove/999999";

        mvc.perform(post(url).with(csrf()).with(user(ADMIN).roles("ADMIN", "ATENDENTE")))
                .andExpect(status().isNotFound());
        mvc.perform(post(url).with(csrf()).with(user(USER).roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(ADMIN).roles("ADMIN", "ATENDENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get(url).with(user(ADMIN).roles("ADMIN", "ATENDENTE")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listaDeMedicosSoMostraAcoesDeAdminParaOAdmin() throws Exception {
        mvc.perform(get("/medicos/list").with(user(ADMIN).roles("ADMIN", "ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/medicos/remove/")))
                .andExpect(content().string(containsString("/medicos/edit/")));
        mvc.perform(get("/medicos/list").with(user(USER).roles("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/medicos/remove/"))))
                .andExpect(content().string(not(containsString("/medicos/edit/"))));
    }

    @Test
    void paginaMeusDadosMostraLoginEPerfisDoUsuarioAutenticado() throws Exception {
        mvc.perform(get("/usuario/dados").with(user(ADMIN).roles("ADMIN", "ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">admin<")))
                .andExpect(content().string(containsString(">ADMIN<")))
                .andExpect(content().string(containsString(">ATENDENTE<")));
        mvc.perform(get("/usuario/dados").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutExigePostComCsrfEEncerraASessao() throws Exception {
        mvc.perform(post("/logout").with(user(USER).roles("ATENDENTE")))
                .andExpect(status().isForbidden());

        var autenticacao = mvc.perform(post("/login").with(csrf())
                        .param("username", USER).param("password", SENHA_USER))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession sessao = (MockHttpSession) autenticacao.getRequest().getSession(false);
        assertThat(sessao).isNotNull();
        mvc.perform(get("/").session(sessao))
                .andExpect(status().isOk());

        mvc.perform(post("/logout").session(sessao).with(csrf()).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/").session(sessao).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void healthCheckEPublicoMasSoOAdminVeOsDetalhes() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")))
                .andExpect(content().string(not(containsString("components"))));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

        mvc.perform(get("/actuator/health").with(user(USER).roles("ATENDENTE")))
                .andExpect(content().string(not(containsString("components"))));
        mvc.perform(get("/actuator/health").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"db\"")));
    }

    @Test
    void outrosEndpointsDoActuatorNaoFicamExpostos() throws Exception {
        mvc.perform(get("/actuator/env").with(user(USER).roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        // Mesmo para o ADMIN: só o health é exposto pela web.
        mvc.perform(get("/actuator/env").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
