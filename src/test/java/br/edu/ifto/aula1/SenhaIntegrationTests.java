package br.edu.ifto.aula1;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.entity.RegistroAuditoria;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.RegistroAuditoriaRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import br.edu.ifto.aula1.security.TentativasLoginService;
import br.edu.ifto.aula1.service.UsuarioService;
import br.edu.ifto.aula1.validation.Credenciais;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Troca da própria senha e redefinição pela administração. */
@SpringBootTest
@AutoConfigureMockMvc
class SenhaIntegrationTests {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();
    private static final String ATUAL = "Primeira-1234";
    private static final String NOVA = "Segunda-5678";

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioService service;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RegistroAuditoriaRepository auditoria;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transacao;
    @Autowired private EntityManagerFactory fabrica;

    private final RequestPostProcessor admin = httpBasic("admin", "Admin123");
    private String login;

    @BeforeEach
    void novaConta() {
        login = "conta.senha" + SEQUENCIA.incrementAndGet();
        Credenciais credenciais = new Credenciais();
        credenciais.setLogin(login);
        credenciais.setSenha(ATUAL);
        service.criar(credenciais, Perfil.PACIENTE);
    }

    @Test
    void trocaASenhaEEncerraAsOutrasSessoes() throws Exception {
        MockHttpSession minha = entrar(ATUAL);
        MockHttpSession outra = entrar(ATUAL);
        mvc.perform(get("/usuario/senha").session(minha)).andExpect(status().isOk());

        trocar(minha, ATUAL, NOVA, NOVA)
                .andExpect(redirectedUrl("/usuario/dados"))
                .andExpect(flash().attribute("mensagem", "Senha alterada com sucesso."));

        mvc.perform(get("/usuario/dados").session(minha)).andExpect(status().isOk());
        mvc.perform(get("/usuario/dados").session(outra)).andExpect(redirectedUrl("/login?expirada"));
        mvc.perform(get("/usuario/dados").with(httpBasic(login, ATUAL))).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuario/dados").with(httpBasic(login, NOVA))).andExpect(status().isOk());
    }

    @Test
    void dadosInvalidosNaoTrocamASenha() throws Exception {
        MockHttpSession sessao = entrar(ATUAL);

        trocar(sessao, "", "", "").andExpect(content().string(containsString("Informe a senha atual.")))
                .andExpect(content().string(containsString("Informe a nova senha.")));
        trocar(sessao, ATUAL, "curta1", "curta1").andExpect(content().string(containsString("de 8 a 72")));
        trocar(sessao, ATUAL, login + "9", login + "9")
                .andExpect(content().string(containsString("não pode conter o nome de usuário")));
        trocar(sessao, ATUAL, ATUAL, ATUAL).andExpect(content().string(containsString("diferente da atual")));
        trocar(sessao, ATUAL, NOVA, NOVA + "x").andExpect(content().string(containsString("As senhas não são iguais.")));
        mvc.perform(post("/usuario/senha").session(sessao)).andExpect(status().isForbidden()); // sem CSRF
        assertThat(senhaConfere(login, ATUAL)).isTrue();
    }

    @Test
    void senhaAtualErradaContaParaOBloqueio() throws Exception {
        MockHttpSession sessao = entrar(ATUAL);
        for (int i = 0; i < TentativasLoginService.LIMITE_TENTATIVAS; i++)
            trocar(sessao, "errada-" + i, NOVA, NOVA)
                    .andExpect(content().string(containsString("A senha atual está incorreta.")));

        trocar(sessao, ATUAL, NOVA, NOVA).andExpect(content().string(containsString("Muitas tentativas.")));
        assertThat(senhaConfere(login, ATUAL)).isTrue();
    }

    @Test
    void administracaoRedefineEADonaDaContaTemQueTrocar() throws Exception {
        MockHttpSession aberta = entrar(ATUAL);

        UsuarioService.SenhaProvisoria provisoria = (UsuarioService.SenhaProvisoria) mvc
                .perform(post("/contas/redefinir/" + conta().getId()).with(csrf()).with(admin))
                .andExpect(redirectedUrl("/contas/list"))
                .andReturn().getFlashMap().get("provisoria");
        assertThat(provisoria.senha()).hasSize(12).containsPattern("[A-Za-z]").containsPattern("\\d");
        mvc.perform(get("/contas/list").with(admin).param("busca", login).flashAttr("provisoria", provisoria))
                .andExpect(content().string(containsString(provisoria.senha())));
        mvc.perform(get("/contas/list").with(admin).param("busca", login))
                .andExpect(content().string(containsString("Senha provisória")))
                .andExpect(content().string(not(containsString(provisoria.senha()))));

        mvc.perform(get("/usuario/dados").session(aberta)).andExpect(redirectedUrl("/login?expirada"));
        MockHttpSession nova = entrar(provisoria.senha());
        mvc.perform(get("/").session(nova)).andExpect(redirectedUrl("/usuario/senha"));
        mvc.perform(get("/usuario/dados/exportar").session(nova)).andExpect(redirectedUrl("/usuario/senha"));
        mvc.perform(get("/usuario/senha").session(nova))
                .andExpect(content().string(containsString("Sua senha é provisória.")));
        trocar(nova, provisoria.senha(), NOVA, NOVA).andExpect(redirectedUrl("/usuario/dados"));
        mvc.perform(get("/").session(nova)).andExpect(status().isOk());
        assertThat(conta().isTrocarSenha()).isFalse();
    }

    @Test
    void soAdministracaoRedefineENuncaAPropria() throws Exception {
        for (RequestPostProcessor pessoa : new RequestPostProcessor[]{httpBasic("atendente", "Atendente123"),
                httpBasic("eduardo.melo", "Medico123"), httpBasic(login, ATUAL)}) {
            mvc.perform(get("/contas/list").with(pessoa)).andExpect(status().isForbidden());
            mvc.perform(post("/contas/redefinir/" + conta().getId()).with(csrf()).with(pessoa))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/contas/redefinir/" + conta().getId()).with(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/contas/redefinir/" + usuarios.findByLogin("admin").orElseThrow().getId())
                        .with(csrf()).with(admin))
                .andExpect(redirectedUrl("/contas/list")).andExpect(flash().attributeExists("erro"));
        mvc.perform(post("/contas/redefinir/999999").with(csrf()).with(admin)).andExpect(status().isNotFound());
        assertThat(senhaConfere("admin", "Admin123")).isTrue();
        assertThat(senhaConfere(login, ATUAL)).isTrue();
    }

    @Test
    void redefinirEncerraTodasAsSessoesEOLembrarDeMim() throws Exception {
        MvcResult entrada = entrarComLembrar();
        Cookie lembrar = entrada.getResponse().getCookie("remember-me");
        MockHttpSession aberta = (MockHttpSession) entrada.getRequest().getSession(false);
        // O cookie, sozinho, abre outra sessão.
        MockHttpSession doCookie = (MockHttpSession) mvc.perform(get("/usuario/dados").cookie(lembrar))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);

        redefinir();

        for (MockHttpSession sessao : new MockHttpSession[]{aberta, doCookie})
            mvc.perform(get("/usuario/dados").session(sessao)).andExpect(redirectedUrl("/login?expirada"));
        mvc.perform(get("/usuario/dados").cookie(lembrar)).andExpect(status().isUnauthorized());
    }

    @Test
    void trocarInvalidaOLembrarDeMimAntigo() throws Exception {
        Cookie lembrar = entrarComLembrar().getResponse().getCookie("remember-me");
        mvc.perform(get("/usuario/dados").cookie(lembrar)).andExpect(status().isOk());

        trocar(entrar(ATUAL), ATUAL, NOVA, NOVA).andExpect(redirectedUrl("/usuario/dados"));

        mvc.perform(get("/usuario/dados").cookie(lembrar)).andExpect(status().isUnauthorized());
    }

    @Test
    void trocaERedefinicaoSaoAuditadasSemASenha() throws Exception {
        String provisoria = redefinir();
        trocar(entrar(provisoria), provisoria, NOVA, NOVA).andExpect(redirectedUrl("/usuario/dados"));

        List<RegistroAuditoria> registros = auditoria
                .findByEntidadeAndEntidadeIdOrderByIdAsc("Usuario", conta().getId());
        assertThat(registros).extracting(RegistroAuditoria::getAcao, RegistroAuditoria::getUsuario)
                .containsExactly(tuple(AcaoAuditoria.SENHA_REDEFINIDA, "admin"),
                        tuple(AcaoAuditoria.SENHA_ALTERADA, login));
        assertThat(registros).extracting(RegistroAuditoria::getDetalhe).containsExactly("Conta " + login, null);
        mvc.perform(get("/auditoria").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Redefiniu a senha de uma conta")))
                .andExpect(content().string(not(containsString(provisoria))));
    }

    @Test
    void redefinirDesfazOBloqueioDaConta() throws Exception {
        MockHttpSession sessao = entrar(ATUAL);
        for (int i = 0; i < TentativasLoginService.LIMITE_TENTATIVAS; i++)
            trocar(sessao, "errada-" + i, NOVA, NOVA);
        // O bloqueio vale também para o login, mesmo com a senha certa.
        mvc.perform(get("/usuario/dados").with(httpBasic(login, ATUAL))).andExpect(status().isUnauthorized());

        String provisoria = redefinir();

        mvc.perform(get("/usuario/senha").with(httpBasic(login, provisoria))).andExpect(status().isOk());
    }

    @Test
    void visitanteNaoAbreContasNemATrocaDeSenha() throws Exception {
        for (String rota : new String[]{"/contas/list", "/usuario/senha"})
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
        mvc.perform(post("/contas/redefinir/" + conta().getId()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/usuario/senha").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void trocaSimultaneaNaoAnulaARedefinicao() throws Exception {
        Long id = conta().getId();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // A redefinição já leu a conta e ainda não confirmou: a troca tem de esperar por ela.
            Future<UsuarioService.ResultadoTroca> troca = transacao.execute(status -> {
                service.redefinirSenha(id, "admin");
                var futura = executor.submit(() -> service.trocarSenha(login, ATUAL, NOVA));
                assertEsperando(futura);
                return futura;
            });
            assertThat(troca.get(30, TimeUnit.SECONDS)).isEqualTo(UsuarioService.ResultadoTroca.SENHA_INCORRETA);
        } finally {
            executor.shutdownNow();
        }
        assertThat(conta().isTrocarSenha()).isTrue();
        assertThat(conta().getTentativasFalhas()).isEqualTo(1);
        assertThat(senhaConfere(login, NOVA)).isFalse();
    }

    @Test
    void redefinicaoSimultaneaATrocaContinuaExigindoNovaTroca() throws Exception {
        Long id = conta().getId();
        String provisoria = service.redefinirSenha(id, "admin").senha();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String outra;
        try {
            Future<UsuarioService.SenhaProvisoria> redefinicao = transacao.execute(status -> {
                assertThat(service.trocarSenha(login, provisoria, NOVA)).isEqualTo(UsuarioService.ResultadoTroca.TROCADA);
                var futura = executor.submit(() -> service.redefinirSenha(id, "admin"));
                assertEsperando(futura);
                return futura;
            });
            outra = redefinicao.get(30, TimeUnit.SECONDS).senha();
        } finally {
            executor.shutdownNow();
        }
        assertThat(conta().isTrocarSenha()).isTrue();
        assertThat(senhaConfere(login, outra)).isTrue();
    }

    @Test
    void loginPorRememberMeNaoZeraAContagemDeFalhasMasEAuditado() throws Exception {
        Cookie lembrar = entrarComLembrar().getResponse().getCookie("remember-me");
        int antes = auditoria.findByUsuarioAndAcaoOrderByIdDesc(login, AcaoAuditoria.LOGIN).size();
        trocar(entrar(ATUAL), "errada-1", NOVA, NOVA);
        int depoisDoFormulario = auditoria.findByUsuarioAndAcaoOrderByIdDesc(login, AcaoAuditoria.LOGIN).size();
        assertThat(depoisDoFormulario).isEqualTo(antes + 1);

        mvc.perform(get("/usuario/dados").cookie(lembrar)).andExpect(status().isOk());

        assertThat(conta().getTentativasFalhas()).isEqualTo(1);
        assertThat(auditoria.findByUsuarioAndAcaoOrderByIdDesc(login, AcaoAuditoria.LOGIN))
                .hasSize(depoisDoFormulario + 1);
    }

    @Test
    void trocaCorretaZeraAContagemDeFalhas() throws Exception {
        MockHttpSession sessao = entrar(ATUAL);
        for (int i = 0; i < TentativasLoginService.LIMITE_TENTATIVAS - 1; i++)
            trocar(sessao, "errada-" + i, NOVA, NOVA);

        trocar(sessao, ATUAL, NOVA, NOVA).andExpect(redirectedUrl("/usuario/dados"));

        assertThat(conta().getTentativasFalhas()).isZero();
    }

    @Test
    void novaSenhaComMaisDe72BytesERecusada() throws Exception {
        String longa = "ç".repeat(40) + "1"; // 41 caracteres, 81 bytes

        trocar(entrar(ATUAL), ATUAL, longa, longa).andExpect(content().string(containsString("A senha é longa demais.")));

        assertThat(senhaConfere(login, ATUAL)).isTrue();
    }

    @Test
    void listagemDeContasNaoConsultaOsPerfisUmaContaPorVez() {
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);
        try {
            estatisticas.clear();
            assertThat(service.listarContas("", 0, 5).getContent()).hasSize(5);
            // A página, o total e os perfis de todas as contas da página.
            assertThat(estatisticas.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        } finally {
            estatisticas.setStatisticsEnabled(false);
        }
    }

    private static void assertEsperando(Future<?> futura) {
        assertThatThrownBy(() -> futura.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
    }

    private boolean senhaConfere(String login, String senha) {
        return passwordEncoder.matches(senha, usuarios.findByLogin(login).orElseThrow().getPassword());
    }

    private String redefinir() throws Exception {
        return ((UsuarioService.SenhaProvisoria) mvc
                .perform(post("/contas/redefinir/" + conta().getId()).with(csrf()).with(admin))
                .andReturn().getFlashMap().get("provisoria")).senha();
    }

    private MvcResult entrarComLembrar() throws Exception {
        return mvc.perform(post("/login").with(csrf())
                .param("username", login).param("password", ATUAL).param("remember-me", "on")).andReturn();
    }

    private Usuario conta() {
        return usuarios.findByLogin(login).orElseThrow();
    }

    private MockHttpSession entrar(String senha) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").user(login).password(senha))
                .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
    }

    private ResultActions trocar(MockHttpSession sessao, String atual, String nova, String confirmacao) throws Exception {
        return mvc.perform(post("/usuario/senha").with(csrf()).session(sessao)
                .param("senhaAtual", atual).param("novaSenha", nova).param("confirmacao", confirmacao));
    }
}
