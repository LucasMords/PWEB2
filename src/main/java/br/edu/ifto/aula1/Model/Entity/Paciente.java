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
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pacientes")
public class Paciente {

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

    @Column(nullable = false, unique = true, length = 14)
    @NotBlank(message = "{paciente.cpf.obrigatorio}")
    @org.hibernate.validator.constraints.br.CPF(message = "{paciente.cpf.formato}")
    private String cpf;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(name = "data_nascimento", nullable = false)
    @NotNull(message = "{paciente.nascimento.obrigatorio}")
    @PastOrPresent(message = "{paciente.nascimento.futuro}")
    private LocalDate dataNascimento;

    @Column(length = 20)
    @Pattern(regexp = "^$|[0-9]{10,11}", message = "{telefone.formato}")
    private String telefone;

    @Column(length = 120)
    @Email(message = "{email.formato}")
    @Size(max = 120, message = "{texto.tamanho}")
    private String email;

    @OneToMany(mappedBy = "paciente")
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

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf == null ? null : cpf.strip().replaceAll("[.\\-]", "");
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
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
