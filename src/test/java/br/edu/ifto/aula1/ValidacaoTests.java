package br.edu.ifto.aula1;

import br.edu.ifto.aula1.Model.Entity.*;
import br.edu.ifto.aula1.Model.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class ValidacaoTests {
    @Autowired WebApplicationContext context;
    @Autowired PacienteRepository pacientes;
    @Autowired MedicoRepository medicos;
    @Autowired ConsultaRepository consultas;
    MockMvc mvc;

    @BeforeEach
    void configurar() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }

    MockHttpServletRequestBuilder paciente(String rota) {
        return post(rota).with(request -> {
            java.util.Map.of("nome", "  Novo paciente  ", "cpf", "390.533.447-05",
                    "dataNascimento", "1990-01-01", "telefone", "(63) 99999-1234", "email", "novo@email.com")
                    .forEach((campo, valor) -> { if (request.getParameter(campo) == null) request.setParameter(campo, valor); });
            return request;
        });
    }

    MockHttpServletRequestBuilder consulta(String rota, LocalDateTime horario) {
        return post(rota).with(request -> {
            java.util.Map.of("paciente.id", "1", "medico.id", "1", "dataHora", horario.toString(), "status", "AGENDADA")
                    .forEach((campo, valor) -> { if (request.getParameter(campo) == null) request.setParameter(campo, valor); });
            return request;
        });
    }

    LocalDateTime futuro() { return LocalDateTime.now().plusDays(10).withSecond(0).withNano(0); }

    @Test void cadastroNormalizaDados() throws Exception {
        mvc.perform(paciente("/pacientes/save")).andExpect(status().is3xxRedirection());
        Paciente salvo = pacientes.findByCpf("39053344705").orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Novo paciente");
        assertThat(salvo.getTelefone()).isEqualTo("63999991234");
    }

    @ParameterizedTest @ValueSource(strings = {"", "111.111.111-11", "123", "abc"})
    void cpfInvalidoNaoPersiste(String cpf) throws Exception {
        long antes = pacientes.count();
        mvc.perform(paciente("/pacientes/save").param("cpf", cpf))
                .andExpect(view().name("paciente/formulario"))
                .andExpect(model().attributeHasFieldErrors("paciente", "cpf"));
        assertThat(pacientes.count()).isEqualTo(antes);
    }

    @Test void camposInvalidosRetornamFormulario() throws Exception {
        mvc.perform(paciente("/pacientes/save").param("nome", "   ")
                .param("dataNascimento", LocalDate.now().plusDays(1).toString())
                .param("email", "invalido").param("telefone", "123"))
                .andExpect(view().name("paciente/formulario"))
                .andExpect(model().attributeHasFieldErrors("paciente", "nome", "dataNascimento", "email", "telefone"));
    }

    @Test void cpfDuplicadoComMascaraNaoPersiste() throws Exception {
        mvc.perform(paciente("/pacientes/save").param("cpf", "529.982.247-25"))
                .andExpect(model().attributeHasFieldErrors("paciente", "cpf"));
    }

    @Test void edicaoAceitaProprioCpfEPreservaVinculos() throws Exception {
        long antes = consultas.count();
        mvc.perform(paciente("/pacientes/update").param("id", "1").param("cpf", "529.982.247-25"))
                .andExpect(status().is3xxRedirection());
        assertThat(consultas.count()).isEqualTo(antes);
    }

    @Test void cadastroNaoPodeSobrescreverRegistro() throws Exception {
        String nome = pacientes.findById(1L).orElseThrow().getNome();
        mvc.perform(paciente("/pacientes/save").param("id", "1"))
                .andExpect(model().attributeHasErrors("paciente"));
        assertThat(pacientes.findById(1L).orElseThrow().getNome()).isEqualTo(nome);
    }

    @Test void crmDuplicadoNormalizado() throws Exception {
        mvc.perform(post("/medicos/save").param("nome", "Medico").param("crm", " crm-to   1001 ")
                        .param("especialidade", "Clinica"))
                .andExpect(model().attributeHasFieldErrors("medico", "crm"));
    }

    @Test void medicoInvalidoNaoPersiste() throws Exception {
        long antes = medicos.count();
        mvc.perform(post("/medicos/save").param("nome", " ").param("crm", "CRM-XX 1")
                        .param("especialidade", " ").param("email", "abc"))
                .andExpect(model().attributeHasFieldErrors("medico", "nome", "crm", "especialidade", "email"));
        assertThat(medicos.count()).isEqualTo(antes);
    }

    @Test void datasMalformadasNaoGeramErroInterno() throws Exception {
        mvc.perform(paciente("/pacientes/save").param("dataNascimento", "2026-02-31"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("paciente", "dataNascimento"));
        mvc.perform(consulta("/consultas/save", futuro()).param("dataHora", "invalido"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("consulta", "dataHora"));
    }

    @Test void consultaSemVinculosRetornaErros() throws Exception {
        mvc.perform(post("/consultas/save"))
                .andExpect(status().isOk()).andExpect(view().name("consulta/formulario"))
                .andExpect(model().attributeHasErrors("consulta"));
    }

    @Test void nascimentoNaoPodeInvalidarHistorico() throws Exception {
        mvc.perform(paciente("/pacientes/update").param("id", "1").param("cpf", "52998224725")
                        .param("dataNascimento", LocalDate.now().toString()))
                .andExpect(model().attributeHasFieldErrors("paciente", "dataNascimento"));
        assertThat(pacientes.findById(1L).orElseThrow().getDataNascimento()).isEqualTo(LocalDate.of(1992, 5, 14));
    }

    @Test void camposNaoPermitidosNaoAlteramVinculos() throws Exception {
        long antes = consultas.count();
        mvc.perform(paciente("/pacientes/update").param("id", "1").param("cpf", "52998224725")
                        .param("consultas[0].id", "1").param("consultas[0].observacoes", "Alteracao indevida"))
                .andExpect(status().is3xxRedirection());
        assertThat(consultas.count()).isEqualTo(antes);
        assertThat(consultas.findById(1L).orElseThrow().getObservacoes()).isNotEqualTo("Alteracao indevida");
    }

    @Test void novaConsultaNaoPodeIniciarFinalizada() throws Exception {
        mvc.perform(consulta("/consultas/save", futuro()).param("status", "REALIZADA"))
                .andExpect(model().attributeHasFieldErrors("consulta", "status"));
    }

    @Test void consultaFuturaNaoPodeSerRealizada() throws Exception {
        LocalDateTime horario = futuro();
        mvc.perform(consulta("/consultas/save", horario)).andExpect(status().is3xxRedirection());
        Long id = consultas.findAll().stream().filter(c -> c.getDataHora().equals(horario)).findFirst().orElseThrow().getId();
        mvc.perform(consulta("/consultas/update", horario).param("id", id.toString()).param("status", "REALIZADA"))
                .andExpect(model().attributeHasFieldErrors("consulta", "status"));
        assertThat(consultas.findById(id).orElseThrow().getStatus()).isEqualTo(StatusConsulta.AGENDADA);
    }

    @Test void limitesDeTamanhoSaoAplicadosNoServidor() throws Exception {
        mvc.perform(paciente("/pacientes/save").param("nome", "a".repeat(121)))
                .andExpect(model().attributeHasFieldErrors("paciente", "nome"));
        mvc.perform(consulta("/consultas/save", futuro()).param("observacoes", "a".repeat(501)))
                .andExpect(model().attributeHasFieldErrors("consulta", "observacoes"));
    }

    @ParameterizedTest @ValueSource(strings = {"/pacientes/list", "/medicos/list", "/consultas/list", "/pacientes/consultas/3", "/medicos/consultas/1"})
    void listagensValidasContinuamFuncionando(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(status().isOk());
    }

    @Test void consultaComVinculosInexistentesNaoPersiste() throws Exception {
        long antes = consultas.count();
        mvc.perform(consulta("/consultas/save", futuro()).param("paciente.id", "999999").param("medico.id", "999999"))
                .andExpect(model().attributeHasFieldErrors("consulta", "paciente.id", "medico.id"));
        assertThat(consultas.count()).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"paciente", "medico", "consulta"})
    void edicaoSemIdRetornaFormulario(String tipo) throws Exception {
        long pacientesAntes = pacientes.count();
        long medicosAntes = medicos.count();
        long consultasAntes = consultas.count();
        MockHttpServletRequestBuilder request = switch (tipo) {
            case "paciente" -> paciente("/pacientes/update");
            case "medico" -> post("/medicos/update").param("nome", "Medico").param("crm", "CRM-TO 123")
                    .param("especialidade", "Clinica");
            default -> consulta("/consultas/update", futuro());
        };
        mvc.perform(request).andExpect(status().isOk()).andExpect(view().name(tipo + "/formulario"))
                .andExpect(model().attributeHasFieldErrors(tipo, "id"))
                .andExpect(flash().attributeCount(0));
        assertThat(pacientes.count()).isEqualTo(pacientesAntes);
        assertThat(medicos.count()).isEqualTo(medicosAntes);
        assertThat(consultas.count()).isEqualTo(consultasAntes);
    }

    @ParameterizedTest @ValueSource(strings = {"paciente.id", "medico.id"})
    void referenciaNulaOuNegativaExibeErroNoCampo(String campo) throws Exception {
        mvc.perform(consulta("/consultas/save", futuro()).param(campo, ""))
                .andExpect(view().name("consulta/formulario"))
                .andExpect(model().attributeHasFieldErrors("consulta", campo));
        mvc.perform(consulta("/consultas/save", futuro()).param(campo, "-1"))
                .andExpect(view().name("consulta/formulario"))
                .andExpect(model().attributeHasFieldErrors("consulta", campo));
    }

    @Test void mensagemDeSucessoApareceSomenteAposSalvar() throws Exception {
        var resposta = mvc.perform(paciente("/pacientes/save"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("mensagem", "Paciente cadastrado com sucesso.")).andReturn();
        mvc.perform(get("/pacientes/list").flashAttrs(resposta.getFlashMap()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paciente cadastrado com sucesso.")));
        mvc.perform(paciente("/pacientes/save").param("nome", " "))
                .andExpect(view().name("paciente/formulario"))
                .andExpect(flash().attributeCount(0));
    }

    @Test void errosPreservamValoresERecebemClasseCss() throws Exception {
        var resposta = mvc.perform(paciente("/pacientes/save").param("nome", "a".repeat(121)))
                .andExpect(view().name("paciente/formulario"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("is-invalid")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Use até 120 caracteres.")))
                .andReturn();
        Paciente preenchido = (Paciente) resposta.getModelAndView().getModel().get("paciente");
        assertThat(preenchido.getNome()).isEqualTo("a".repeat(121));
        assertThat(preenchido.getEmail()).isEqualTo("novo@email.com");
    }

    @Test void consultaPassadaOuStatusInvalidoNaoPersiste() throws Exception {
        mvc.perform(consulta("/consultas/save", LocalDateTime.now().minusDays(1)))
                .andExpect(model().attributeHasFieldErrors("consulta", "dataHora"));
        mvc.perform(consulta("/consultas/save", futuro()).param("status", "DESCONHECIDO"))
                .andExpect(model().attributeHasFieldErrors("consulta", "status"));
    }

    @Test void conflitoDeMedicoEPacienteNaoPersiste() throws Exception {
        LocalDateTime horario = futuro();
        mvc.perform(consulta("/consultas/save", horario)).andExpect(status().is3xxRedirection());
        long antes = consultas.count();
        mvc.perform(consulta("/consultas/save", horario).param("paciente.id", "2"))
                .andExpect(model().attributeHasFieldErrors("consulta", "dataHora"));
        mvc.perform(consulta("/consultas/save", horario).param("medico.id", "2"))
                .andExpect(model().attributeHasFieldErrors("consulta", "dataHora"));
        assertThat(consultas.count()).isEqualTo(antes);
    }

    @Test void edicaoNaoConflitaComPropriaConsulta() throws Exception {
        LocalDateTime horario = futuro();
        mvc.perform(consulta("/consultas/save", horario)).andExpect(status().is3xxRedirection());
        Long id = consultas.findAll().stream().filter(c -> c.getDataHora().equals(horario)).findFirst().orElseThrow().getId();
        mvc.perform(consulta("/consultas/update", horario).param("id", id.toString()).param("observacoes", "Retorno"))
                .andExpect(status().is3xxRedirection());
        assertThat(consultas.findById(id).orElseThrow().getObservacoes()).isEqualTo("Retorno");
    }

    @Test void canceladaLiberaHorario() throws Exception {
        LocalDateTime horario = futuro();
        mvc.perform(consulta("/consultas/save", horario)).andExpect(status().is3xxRedirection());
        Long id = consultas.findAll().stream().filter(c -> c.getDataHora().equals(horario)).findFirst().orElseThrow().getId();
        mvc.perform(consulta("/consultas/update", horario).param("id", id.toString()).param("status", "CANCELADA"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(consulta("/consultas/save", horario)).andExpect(status().is3xxRedirection());
    }

    @Test void realizadaPermiteObservacoesMasPreservaHistorico() throws Exception {
        Consulta historico = consultas.findById(1L).orElseThrow();
        mvc.perform(consulta("/consultas/update", historico.getDataHora()).param("id", "1")
                        .param("medico.id", historico.getMedico().getId().toString())
                        .param("status", "REALIZADA").param("observacoes", "Complemento"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(consulta("/consultas/update", futuro()).param("id", "1").param("status", "AGENDADA"))
                .andExpect(model().attributeHasErrors("consulta"));
        assertThat(consultas.findById(1L).orElseThrow().getStatus()).isEqualTo(StatusConsulta.REALIZADA);
    }

    @ParameterizedTest @ValueSource(strings = {"paciente.id", "medico.id"})
    void realizadaNaoPermiteTrocarParticipantes(String campo) throws Exception {
        Consulta historico = consultas.findById(1L).orElseThrow();
        Long pacienteId = historico.getPaciente().getId();
        Long medicoId = historico.getMedico().getId();
        String outroId = campo.equals("paciente.id") ? "2" : "1";

        mvc.perform(consulta("/consultas/update", historico.getDataHora()).param("id", "1")
                        .param("paciente.id", campo.equals("paciente.id") ? outroId : pacienteId.toString())
                        .param("medico.id", campo.equals("medico.id") ? outroId : medicoId.toString())
                        .param("status", "REALIZADA"))
                .andExpect(view().name("consulta/formulario"))
                .andExpect(model().attributeHasErrors("consulta"));

        assertThat(historico.getPaciente().getId()).isEqualTo(pacienteId);
        assertThat(historico.getMedico().getId()).isEqualTo(medicoId);
    }

    @Test void reagendamentoParaPassadoPreservaHorarioOriginal() throws Exception {
        LocalDateTime horario = futuro();
        mvc.perform(consulta("/consultas/save", horario)).andExpect(status().is3xxRedirection());
        Consulta atual = consultas.findAll().stream().filter(c -> c.getDataHora().equals(horario)).findFirst().orElseThrow();

        mvc.perform(consulta("/consultas/update", horario.minusYears(1)).param("id", atual.getId().toString()))
                .andExpect(model().attributeHasFieldErrors("consulta", "dataHora"));

        assertThat(atual.getDataHora()).isEqualTo(horario);
    }

    @ParameterizedTest @ValueSource(strings = {"/pacientes/remove/1", "/medicos/remove/1", "/consultas/remove/1"})
    void exclusaoPreservaHistorico(String rota) throws Exception {
        long antes = consultas.count();
        mvc.perform(post(rota)).andExpect(status().isConflict()).andExpect(view().name("erro"));
        assertThat(consultas.count()).isEqualTo(antes);
    }

    @ParameterizedTest @ValueSource(strings = {"/pacientes/remove/1", "/medicos/remove/1", "/consultas/remove/1"})
    void getNaoExclui(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(status().isMethodNotAllowed());
    }

    @Test void exclusaoSemVinculosFunciona() throws Exception {
        mvc.perform(paciente("/pacientes/save")).andExpect(status().is3xxRedirection());
        Long id = pacientes.findByCpf("39053344705").orElseThrow().getId();
        mvc.perform(post("/pacientes/remove/" + id)).andExpect(status().is3xxRedirection());
        assertThat(pacientes.existsById(id)).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"/pacientes/edit/999999", "/medicos/consultas/999999", "/consultas/edit/999999"})
    void registroInexistenteRetorna404(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(status().isNotFound()).andExpect(view().name("erro"));
    }

    @ParameterizedTest @ValueSource(strings = {"/pacientes/consultas/-1", "/medicos/edit/abc", "/consultas/edit/0"})
    void identificadorInvalidoRetorna400(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(status().isBadRequest()).andExpect(view().name("erro"));
    }
}
