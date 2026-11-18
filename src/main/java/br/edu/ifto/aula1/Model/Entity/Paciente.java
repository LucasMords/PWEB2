package br.edu.ifto.aula1.Model.Entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pacientes")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120, message = "O nome deve ter no máximo {max} caracteres.")
    @Column(nullable = false, length = 120)
    private String nome;

    // O setter padroniza o formato (000.000.000-00) e o @CPF confere os dígitos verificadores.
    // O @CPF sozinho aceita o CPF com ou sem pontuação, por isso o setter continua necessário.
    @NotBlank(message = "Informe o CPF.")
    @CPF(message = "Informe um CPF válido.")
    @Column(nullable = false, unique = true, length = 14)
    private String cpf;

    @NotNull(message = "Informe a data de nascimento.")
    @Past(message = "A data de nascimento deve estar no passado.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Size(max = 20, message = "O telefone não pode ter mais que {max} caracteres.")
    @Column(length = 20)
    private String telefone;

    @Email(message = "Informe um e-mail válido.")
    @Size(max = 120, message = "O email não pode ter mais que {max} caracteres.")
    @Column(length = 120)
    private String email;

    // Conta de acesso do paciente. Fica sem getter/setter de propósito: assim o formulário
    // não consegue preencher nada dela; quem a vincula é o serviço, em vincularUsuario().
    // Pacientes antigos (import.sql) não têm conta, por isso a coluna aceita null.
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    // LGPD: quando o paciente (ou a recepção, em nome dele) aceitou a política de privacidade.
    // Sem setter, pelo mesmo motivo da conta: o formulário não preenche; o serviço registra.
    @Column(name = "consentimento_em")
    private LocalDateTime consentimentoEm;

    public void vincularUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public void registrarConsentimento(LocalDateTime quando) {
        this.consentimentoEm = quando;
    }

    public LocalDateTime getConsentimentoEm() {
        return consentimentoEm;
    }

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
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = formatarCpf(cpf);
    }

    /**
     * Padroniza o CPF como 000.000.000-00, tenha ele sido digitado com ou sem pontuação,
     * para que o banco nunca guarde o mesmo CPF em dois formatos.
     * Se não houver 11 dígitos, devolve o texto como veio e a validação o rejeita.
     */
    private static String formatarCpf(String cpf) {
        // Em branco vira null: o @CPF ignora null, então só o @NotBlank ("Informe o CPF.") aparece.
        if (cpf == null || cpf.isBlank())
            return null;

        String digitos = cpf.replaceAll("\\D", "");
        if (digitos.length() != 11)
            return cpf.trim();

        return digitos.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
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
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
