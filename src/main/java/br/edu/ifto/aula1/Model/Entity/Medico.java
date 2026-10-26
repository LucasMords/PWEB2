package br.edu.ifto.aula1.Model.Entity;

import br.edu.ifto.aula1.Validation.Cadastro;
import br.edu.ifto.aula1.Validation.Edicao;
import br.edu.ifto.aula1.Validation.Referencia;
import jakarta.validation.constraints.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "medicos")
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Null(groups = Cadastro.class, message = "{registro.id.cadastro}")
    @NotNull(groups = {Edicao.class, Referencia.class}, message = "{registro.id.obrigatorio}")
    @Positive(groups = {Edicao.class, Referencia.class}, message = "{registro.id.positivo}")
    private Long id;

    @Column(nullable = false, length = 120)
    @NotBlank(message = "{nome.obrigatorio}")
    @Size(max = 120, message = "{texto.tamanho}")
    private String nome;

    @Column(nullable = false, unique = true, length = 20)
    @NotBlank(message = "{medico.crm.obrigatorio}")
    @Pattern(regexp = "CRM-(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO) [0-9]{1,6}", message = "{medico.crm.formato}")
    private String crm;

    @Column(nullable = false, length = 80)
    @NotBlank(message = "{medico.especialidade.obrigatoria}")
    @Size(max = 80, message = "{texto.tamanho}")
    private String especialidade;

    @Column(length = 20)
    @Pattern(regexp = "^$|[0-9]{10,11}", message = "{telefone.formato}")
    private String telefone;

    @Column(length = 120)
    @Email(message = "{email.formato}")
    @Size(max = 120, message = "{texto.tamanho}")
    private String email;

    @OneToMany(mappedBy = "medico")
    @OrderBy("dataHora DESC")
    private List<Consulta> consultas = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome == null ? null : nome.strip();
    }

    public String getCrm() {
        return crm;
    }

    public void setCrm(String crm) {
        this.crm = crm == null ? null : crm.strip().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade == null ? null : especialidade.strip();
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone == null ? null : telefone.strip().replaceAll("[() .\\-]", "");
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    public List<Consulta> getConsultas() {
        return consultas;
    }

    public void setConsultas(List<Consulta> consultas) {
        this.consultas = consultas;
    }
}
