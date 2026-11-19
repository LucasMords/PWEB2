package br.edu.ifto.aula1;

import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.MedicoRepository;
import br.edu.ifto.aula1.model.repository.PacienteRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import br.edu.ifto.aula1.service.PacienteService;
import br.edu.ifto.aula1.validation.Credenciais;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cadastro de paciente e de médico junto com a conta de acesso (Pessoa associada a Usuario). */
@SpringBootTest
@AutoConfigureMockMvc
class CadastroComContaIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PacienteRepository pacientes;
    @Autowired private MedicoRepository medicos;
    @Autowired private PacienteService pacienteService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void formularioDePacienteEPublicoEPedeUsuarioESenha() throws Exception {
        mvc.perform(get("/pacientes/form"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"login\"")))
                .andExpect(content().string(containsString("name=\"senha\"")));
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("/pacientes/form")));
    }

    @Test
    void visitanteSeCadastraGanhaContaDePacienteESoVeOQueEDele() throws Exception {
        mvc.perform(post("/pacientes/save").with(csrf())
                        .param("nome", "Joana Prado").param("cpf", "11144477735")
                        .param("dataNascimento", "1990-03-20")
                        .param("login", "joana.prado").param("senha", "Joana-123")
                        .param("consentimento", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("mensagem"));

        // A conta existe, com a senha em hash e o papel inicial PACIENTE, ligada ao paciente.
        Usuario conta = usuarios.findComPerfisByLogin("joana.prado").orElseThrow();
        assertThat(conta.getPassword()).startsWith("$2").isNotEqualTo("Joana-123");
        assertThat(passwordEncoder.matches("Joana-123", conta.getPassword())).isTrue();
        assertThat(conta.getPerfis()).extracting(Perfil::getNome).containsExactly(Perfil.PACIENTE);
        assertThat(pacientes.findByUsuarioLogin("joana.prado")).get()
                .extracting(Paciente::getNome).isEqualTo("Joana Prado");

        // Ela consegue entrar com o usuário e a senha que escolheu.
        MockHttpSession sessao = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", "joana.prado").param("password", "Joana-123"))
                .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);

        mvc.perform(get("/").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/usuario/consultas")))
                .andExpect(content().string(not(containsString("/pacientes/list"))));
        mvc.perform(get("/usuario/dados").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Joana Prado")))
                .andExpect(content().string(containsString(">PACIENTE<")));
        mvc.perform(get("/usuario/consultas").session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nenhuma consulta encontrada.")));

        // Mas não enxerga os cadastros nem a agenda da clínica.
        for (String url : new String[]{"/pacientes/list", "/pacientes/edit/1", "/pacientes/consultas/1",
                "/consultas/list", "/consultas/form", "/medicos/list"})
            mvc.perform(get(url).session(sessao)).andExpect(status().isForbidden());
    }

    @Test
    void cadastroRejeitaLoginRepetidoESenhaCurtaSemGravarNada() throws Exception {
        long pacientesAntes = pacientes.count();
        long usuariosAntes = usuarios.count();

        mvc.perform(post("/pacientes/save").with(csrf())
                        .param("nome", "Pedro Nunes").param("cpf", "39053344705")
                        .param("dataNascimento", "1988-07-01")
                        .param("login", "admin").param("senha", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Este usuário já está em uso.")))
                .andExpect(content().string(containsString("A senha deve ter de 8 a 72 caracteres.")))
                .andExpect(content().string(containsString("Não foi possível salvar.")))
                // O que foi digitado volta para o formulário, menos a senha.
                .andExpect(content().string(containsString("value=\"Pedro Nunes\"")))
                .andExpect(content().string(not(containsString("value=\"123\""))));

        mvc.perform(post("/pacientes/save").with(csrf())
                        .param("nome", "Pedro Nunes").param("cpf", "39053344705")
                        .param("dataNascimento", "1988-07-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe o usuário.")))
                .andExpect(content().string(containsString("Informe a senha.")));

        assertThat(pacientes.count()).isEqualTo(pacientesAntes);
        assertThat(usuarios.count()).isEqualTo(usuariosAntes);
    }

    @Test
    void requisicaoNaoConsegueEscolherPerfilNemPreencherAContaDiretamente() throws Exception {
        mvc.perform(post("/pacientes/save").with(csrf())
                        .param("nome", "Rui Farias").param("cpf", "16899535009")
                        .param("dataNascimento", "1979-11-05")
                        .param("login", "rui.farias").param("senha", "Rui-12345").param("consentimento", "true")
                        // Tentativas de se promover ou de mexer na entidade Usuario.
                        .param("perfil", "ADMIN").param("usuario.login", "invasor")
                        .param("usuario.perfis[0].nome", "ADMIN"))
                .andExpect(status().is3xxRedirection());

        assertThat(usuarios.existsByLogin("invasor")).isFalse();
        assertThat(usuarios.findComPerfisByLogin("rui.farias").orElseThrow().getPerfis())
                .extracting(Perfil::getNome).containsExactly(Perfil.PACIENTE);
    }

    @Test
    void adminCadastraMedicoComContaInstitucional() throws Exception {
        String[] dados = {"nome", "Dr. Caio Braga", "crm", "to9001", "especialidade", "Ortopedia",
                "login", "caio.braga", "senha", "Caio-12345"};

        // Só o ADMIN cria médicos: visitante vai para o login e a recepção recebe 403.
        mvc.perform(comParametros(dados).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(comParametros(dados).with(user("atendente").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        assertThat(usuarios.existsByLogin("caio.braga")).isFalse();

        mvc.perform(comParametros(dados).with(user("admin").roles("ADMIN", "ATENDENTE")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/medicos/list"));

        assertThat(usuarios.findComPerfisByLogin("caio.braga").orElseThrow().getPerfis())
                .extracting(Perfil::getNome).containsExactly(Perfil.MEDICO);
        assertThat(medicos.findByUsuarioLogin("caio.braga")).isPresent();

        // O médico entra com a conta criada: trabalha com pacientes e consultas, mas não administra.
        var medico = httpBasic("caio.braga", "Caio-12345");
        mvc.perform(get("/pacientes/list").with(medico)).andExpect(status().isOk());
        mvc.perform(get("/usuario/consultas").with(medico)).andExpect(status().isOk());
        mvc.perform(get("/usuario/dados").with(medico))
                .andExpect(content().string(containsString("Dr. Caio Braga (CRM-TO 9001)")));
        mvc.perform(get("/medicos/form").with(medico)).andExpect(status().isForbidden());
    }

    @Test
    void pacientesEMedicosDoImportSqlJaTemContaEVeemAsPropriasConsultas() throws Exception {
        // Os hashes dos dados de demonstração correspondem às senhas documentadas e cada conta tem um só perfil.
        for (String login : new String[]{"ana.souza", "carlos.lima", "mariana.alves"}) {
            Usuario conta = usuarios.findComPerfisByLogin(login).orElseThrow();
            assertThat(passwordEncoder.matches("Paciente123", conta.getPassword())).as(login).isTrue();
            assertThat(conta.getPerfis()).extracting(Perfil::getNome).containsExactly(Perfil.PACIENTE);
            assertThat(pacientes.findByUsuarioLogin(login)).as(login).isPresent();
        }
        for (String login : new String[]{"beatriz.rocha", "eduardo.melo"}) {
            Usuario conta = usuarios.findComPerfisByLogin(login).orElseThrow();
            assertThat(passwordEncoder.matches("Medico123", conta.getPassword())).as(login).isTrue();
            assertThat(conta.getPerfis()).extracting(Perfil::getNome).containsExactly(Perfil.MEDICO);
            assertThat(medicos.findByUsuarioLogin(login)).as(login).isPresent();
        }

        // Carlos só tem a consulta com o Dr. Eduardo; não vê as da Ana nem a lista geral.
        var carlos = httpBasic("carlos.lima", "Paciente123");
        mvc.perform(get("/usuario/consultas").with(carlos))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Primeira consulta.")))
                .andExpect(content().string(not(containsString("Ana Souza"))));
        mvc.perform(get("/usuario/dados").with(carlos))
                .andExpect(content().string(containsString("Carlos Lima")));
        mvc.perform(get("/consultas/list").with(carlos)).andExpect(status().isForbidden());

        // A Dra. Beatriz vê a consulta que atende (da Ana) e, como equipe, a lista de pacientes.
        var beatriz = httpBasic("beatriz.rocha", "Medico123");
        mvc.perform(get("/usuario/consultas").with(beatriz))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Retorno para avaliacao de exames.")))
                .andExpect(content().string(not(containsString("Primeira consulta."))));
        mvc.perform(get("/pacientes/list").with(beatriz)).andExpect(status().isOk());
    }

    @Test
    void excluirOPacienteRemoveTambemAContaDele() throws Exception {
        Paciente paciente = new Paciente();
        paciente.setNome("Lia Temporaria");
        paciente.setCpf("935.411.347-80");
        paciente.setDataNascimento(LocalDate.of(1995, 1, 1));
        Credenciais credenciais = new Credenciais();
        credenciais.setLogin("lia.temporaria");
        credenciais.setSenha("Lia-12345");
        Long id = pacienteService.cadastrar(paciente, credenciais, true).getId();
        assertThat(usuarios.existsByLogin("lia.temporaria")).isTrue();

        mvc.perform(post("/pacientes/remove/" + id).with(csrf()).with(user("admin").roles("ADMIN", "ATENDENTE")))
                .andExpect(status().is3xxRedirection());

        assertThat(pacientes.existsById(id)).isFalse();
        assertThat(usuarios.existsByLogin("lia.temporaria")).isFalse();
    }

    @Test
    void edicaoNaoMexeNaContaENaoPedeCredenciais() throws Exception {
        var recepcao = user("atendente").roles("ATENDENTE");

        mvc.perform(get("/pacientes/edit/1").with(recepcao))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"senha\""))));
        // Erro de validação na edição reexibe o formulário, ainda sem os dados de acesso.
        mvc.perform(post("/pacientes/update").with(csrf()).with(recepcao)
                        .param("id", "1").param("nome", "").param("cpf", "52998224725")
                        .param("dataNascimento", "1992-05-14"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe o nome.")))
                .andExpect(content().string(not(containsString("name=\"senha\""))));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder comParametros(
            String[] paresNomeValor) {
        var requisicao = post("/medicos/save").with(csrf());
        for (int i = 0; i < paresNomeValor.length; i += 2)
            requisicao.param(paresNomeValor[i], paresNomeValor[i + 1]);
        return requisicao;
    }
}
