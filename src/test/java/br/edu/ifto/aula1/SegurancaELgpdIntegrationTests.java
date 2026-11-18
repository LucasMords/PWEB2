package br.edu.ifto.aula1;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Agenda;
import br.edu.ifto.aula1.model.entity.Agendamento;
import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.model.entity.RegistroAuditoria;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.model.repository.MedicoRepository;
import br.edu.ifto.aula1.model.repository.PacienteRepository;
import br.edu.ifto.aula1.model.repository.RegistroAuditoriaRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import br.edu.ifto.aula1.security.TentativasLoginService;
import br.edu.ifto.aula1.service.AgendaService;
import br.edu.ifto.aula1.service.PacienteService;
import br.edu.ifto.aula1.validation.Credenciais;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bloqueio por tentativas, política de senha, cabeçalhos de segurança, auditoria e LGPD. */
@SpringBootTest
@AutoConfigureMockMvc
class SegurancaELgpdIntegrationTests {

    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @Autowired private MockMvc mvc;
    @Autowired private PacienteService pacienteService;
    @Autowired private AgendaService agendaService;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PacienteRepository pacientes;
    @Autowired private MedicoRepository medicos;
    @Autowired private AgendamentoRepository agendamentos;
    @Autowired private RegistroAuditoriaRepository auditoria;
    @Autowired private JdbcTemplate jdbc;

    // ---------- Bloqueio por tentativas ----------

    @Test
    void cincoSenhasErradasBloqueiamAContaAteMesmoParaASenhaCerta() throws Exception {
        String login = novaConta("Senha-1234");

        for (int i = 0; i < TentativasLoginService.LIMITE_TENTATIVAS; i++)
            mvc.perform(get("/").with(httpBasic(login, "errada-" + i))).andExpect(status().isUnauthorized());

        Usuario usuario = usuarios.findByLogin(login).orElseThrow();
        assertThat(usuario.getBloqueadoAte()).isAfter(LocalDateTime.now().plusMinutes(14));
        // Bloqueada: nem a senha certa entra.
        mvc.perform(get("/").with(httpBasic(login, "Senha-1234"))).andExpect(status().isUnauthorized());

        assertThat(auditoria.findByUsuarioAndAcaoOrderByIdDesc(login, AcaoAuditoria.LOGIN_FALHOU))
                .hasSize(TentativasLoginService.LIMITE_TENTATIVAS);
        assertThat(auditoria.findByUsuarioAndAcaoOrderByIdDesc(login, AcaoAuditoria.CONTA_BLOQUEADA)).hasSize(1);

        // Passado o tempo do bloqueio, a senha certa volta a funcionar e a contagem recomeça.
        jdbc.update("update usuarios set bloqueado_ate = ? where login = ?", LocalDateTime.now().minusMinutes(1), login);
        mvc.perform(get("/").with(httpBasic(login, "Senha-1234"))).andExpect(status().isOk());
        assertThat(usuarios.findByLogin(login).orElseThrow().getBloqueadoAte()).isNull();
    }

    @Test
    void acertarASenhaZeraAsTentativas() throws Exception {
        String login = novaConta("Senha-1234");

        for (int i = 0; i < TentativasLoginService.LIMITE_TENTATIVAS - 1; i++)
            mvc.perform(get("/").with(httpBasic(login, "errada"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/").with(httpBasic(login, "Senha-1234"))).andExpect(status().isOk());
        for (int i = 0; i < TentativasLoginService.LIMITE_TENTATIVAS - 1; i++)
            mvc.perform(get("/").with(httpBasic(login, "errada"))).andExpect(status().isUnauthorized());

        Usuario usuario = usuarios.findByLogin(login).orElseThrow();
        assertThat(usuario.getBloqueadoAte()).isNull();
        assertThat(usuario.getTentativasFalhas()).isEqualTo(TentativasLoginService.LIMITE_TENTATIVAS - 1);
    }

    @Test
    void loginPeloFormularioFicaNaAuditoriaEAMensagemDeErroEGenerica() throws Exception {
        int antes = auditoria.findByUsuarioAndAcaoOrderByIdDesc("atendente", AcaoAuditoria.LOGIN).size();

        mvc.perform(formLogin("/login").user("atendente").password("Atendente123"))
                .andExpect(redirectedUrl("/"));
        assertThat(auditoria.findByUsuarioAndAcaoOrderByIdDesc("atendente", AcaoAuditoria.LOGIN)).hasSize(antes + 1);

        // Usuário inexistente e senha errada levam à mesma tela.
        mvc.perform(formLogin("/login").user("ninguem.aqui").password("x")).andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("Usuário ou senha inválidos.")));
    }

    // ---------- Política de senha ----------

    @Test
    void senhaPrecisaDeLetrasENumerosENaoPodeConterOUsuario() throws Exception {
        long antes = usuarios.count();

        mvc.perform(post("/pacientes/save").with(csrf()).param("consentimento", "true")
                        .param("nome", "Teste Senha").param("cpf", "71428793860").param("dataNascimento", "1990-01-01")
                        .param("login", "teste.senha").param("senha", "somenteletras"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A senha deve ter letras e números.")));
        mvc.perform(post("/pacientes/save").with(csrf()).param("consentimento", "true")
                        .param("nome", "Teste Senha").param("cpf", "71428793860").param("dataNascimento", "1990-01-01")
                        .param("login", "teste.senha").param("senha", "Teste.Senha123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A senha não pode conter o nome de usuário.")));

        assertThat(usuarios.count()).isEqualTo(antes);
    }

    // ---------- Cabeçalhos de segurança ----------

    @Test
    void respostasTrazemCspEOutrosCabecalhosDeSeguranca() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self' https://cdn.jsdelivr.net")))
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-inline"))))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));

        // As páginas não usam JavaScript inline (que a CSP bloquearia).
        mvc.perform(get("/pacientes/list").with(user("admin").roles("ADMIN", "ATENDENTE")))
                .andExpect(content().string(not(containsString("onsubmit="))))
                .andExpect(content().string(containsString("data-confirmar=")));
    }

    // ---------- Auditoria ----------

    @Test
    void operacoesFicamNaAuditoriaComQuemFezEOsDesfeitosNao() throws Exception {
        Agenda agenda = novoHorario();

        Long ana = pacientes.findByUsuarioLogin("ana.souza").orElseThrow().getId();
        Long carlos = pacientes.findByUsuarioLogin("carlos.lima").orElseThrow().getId();

        mvc.perform(post("/agendamentos/save").with(csrf()).with(user("atendente").roles("ATENDENTE"))
                        .param("agendaId", agenda.getId().toString()).param("pacienteId", ana.toString()))
                .andExpect(redirectedUrl("/agendamentos/list"));
        Agendamento agendamento = agendamentos.findByAgendaIdOrderByIdAsc(agenda.getId()).getFirst();

        List<RegistroAuditoria> registros = auditoria.findByEntidadeAndEntidadeIdOrderByIdAsc("Agendamento", agendamento.getId());
        assertThat(registros).extracting(RegistroAuditoria::getAcao).containsExactly(AcaoAuditoria.AGENDAMENTO_CRIADO);
        assertThat(registros.getFirst().getUsuario()).isEqualTo("atendente");

        // Tentativa rejeitada (horário já ocupado): a transação é desfeita e nada vai para a auditoria.
        long totalAntes = auditoria.count();
        mvc.perform(post("/agendamentos/save").with(csrf()).with(user("admin").roles("ADMIN", "ATENDENTE"))
                        .param("agendaId", agenda.getId().toString()).param("pacienteId", carlos.toString()))
                .andExpect(redirectedUrl("/agenda/list"));
        assertThat(auditoria.count()).isEqualTo(totalAntes);

        mvc.perform(post("/agendamentos/cancelar/" + agendamento.getId()).with(csrf()).with(user("admin").roles("ADMIN", "ATENDENTE"))
                        .param("motivo", "Teste"))
                .andExpect(redirectedUrl("/agendamentos/list"));
        assertThat(auditoria.findByEntidadeAndEntidadeIdOrderByIdAsc("Agendamento", agendamento.getId()))
                .extracting(RegistroAuditoria::getAcao, RegistroAuditoria::getUsuario)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AcaoAuditoria.AGENDAMENTO_CRIADO, "atendente"),
                        org.assertj.core.groups.Tuple.tuple(AcaoAuditoria.AGENDAMENTO_CANCELADO, "admin"));
    }

    @Test
    void soAAdministracaoVeAAuditoria() throws Exception {
        mvc.perform(get("/auditoria").with(user("admin").roles("ADMIN", "ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Auditoria")));
        mvc.perform(get("/auditoria").with(user("admin").roles("ADMIN", "ATENDENTE"))
                        .param("acao", "INVALIDA").param("de", "ontem").param("page", "-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/auditoria").with(user("atendente").roles("ATENDENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/auditoria").with(httpBasic("ana.souza", "Paciente123"))).andExpect(status().isForbidden());
    }

    // ---------- LGPD ----------

    @Test
    void cadastroExigeOAceiteDaPoliticaDePrivacidadeEGuardaQuando() throws Exception {
        long antes = pacientes.count();
        String cpf = cpfValido();

        mvc.perform(post("/pacientes/save").with(csrf())
                        .param("nome", "Lia Matos").param("cpf", cpf).param("dataNascimento", "1995-02-02")
                        .param("login", "lia.matos").param("senha", "Senha-1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("É preciso aceitar a política de privacidade")));
        assertThat(pacientes.count()).isEqualTo(antes);

        mvc.perform(post("/pacientes/save").with(csrf()).param("consentimento", "true")
                        .param("nome", "Lia Matos").param("cpf", cpf).param("dataNascimento", "1995-02-02")
                        .param("login", "lia.matos").param("senha", "Senha-1234"))
                .andExpect(redirectedUrl("/login"));
        assertThat(pacientes.findByUsuarioLogin("lia.matos").orElseThrow().getConsentimentoEm()).isNotNull();

        // A política é pública e o formulário aponta para ela.
        mvc.perform(get("/privacidade")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lei nº 13.709/2018")));
        mvc.perform(get("/pacientes/form")).andExpect(content().string(containsString("/privacidade")));
    }

    @Test
    void pacienteBaixaOsPropriosDadosEmJsonEAEquipeNao() throws Exception {
        mvc.perform(get("/usuario/dados/exportar").with(httpBasic("ana.souza", "Paciente123")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("\"nome\":\"Ana Souza\"")))
                .andExpect(content().string(containsString("\"cpf\":\"529.982.247-25\"")))
                .andExpect(content().string(containsString("\"login\":\"ana.souza\"")))
                .andExpect(content().string(containsString("Consulta de rotina.")))
                .andExpect(content().string(not(containsString("Carlos Lima"))))
                .andExpect(content().string(not(containsString("password"))));

        assertThat(auditoria.findByUsuarioAndAcaoOrderByIdDesc("ana.souza", AcaoAuditoria.DADOS_EXPORTADOS)).isNotEmpty();

        // Conta da equipe não tem dados de paciente; sem login, não há exportação.
        mvc.perform(get("/usuario/dados/exportar").with(user("atendente").roles("ATENDENTE"))).andExpect(status().isNotFound());
        mvc.perform(get("/usuario/dados/exportar")).andExpect(status().isUnauthorized());
        // A página "Meus dados" do paciente oferece o download.
        mvc.perform(get("/usuario/dados").with(httpBasic("ana.souza", "Paciente123")))
                .andExpect(content().string(containsString("/usuario/dados/exportar")));
    }

    // ---------- Apoio ----------

    /** Conta de paciente nova, só deste teste, para não bloquear as contas usadas pelos outros. */
    private String novaConta(String senha) {
        String login = "bloqueio." + SEQUENCIA.incrementAndGet();
        Paciente paciente = new Paciente();
        paciente.setNome("Teste " + login);
        paciente.setCpf(cpfValido());
        paciente.setDataNascimento(LocalDate.of(1990, 1, 1));

        Credenciais credenciais = new Credenciais();
        credenciais.setLogin(login);
        credenciais.setSenha(senha);
        pacienteService.cadastrar(paciente, credenciais, true);
        return login;
    }

    private Agenda novoHorario() {
        var form = new br.edu.ifto.aula1.dto.AgendaForm();
        form.setMedicoId(medicos.findByUsuarioLogin("beatriz.rocha").orElseThrow().getId());
        form.setData(LocalDate.now().plusYears(3).plusDays(SEQUENCIA.incrementAndGet()));
        form.setHorario(LocalTime.of(9, 0));
        return agendaService.cadastrar(form);
    }

    /** CPF válido e diferente a cada chamada (os 9 primeiros dígitos variam; os verificadores são calculados). */
    private static String cpfValido() {
        String base = String.format("%09d", 300_000_000 + SEQUENCIA.incrementAndGet() * 7919);
        int d1 = digito(base, 10);
        int d2 = digito(base + d1, 11);
        return base + d1 + d2;
    }

    private static int digito(String numeros, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < numeros.length(); i++)
            soma += (numeros.charAt(i) - '0') * (pesoInicial - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
