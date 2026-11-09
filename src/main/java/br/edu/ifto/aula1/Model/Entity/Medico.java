package br.edu.ifto.aula1.Model.Entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "medicos")
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120, message = "O nome deve ter no máximo {max} caracteres.")
    @Column(nullable = false, length = 120)
    private String nome;

    // O setter já padroniza para "CRM-UF 0000"; aqui só se confere o resultado.
    // O "?" deixa o vazio passar para que apenas o @NotBlank responda por ele.
    @NotBlank(message = "Informe o CRM.")
    @Pattern(regexp = "(CRM-(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO) \\d{1,6})?",
            message = "Informe a UF e o número do CRM, por exemplo: CRM-TO 1234.")
    @Column(nullable = false, unique = true, length = 20)
    private String crm;

    @NotBlank(message = "Informe a especialidade.")
    @Size(max = 80, message = "A especialidade não pode ter mais que {max} caracteres.")
    @Column(nullable = false, length = 80)
    private String especialidade;

    @Size(max = 20, message = "O telefone não pode ter mais que {max} caracteres.")
    @Column(length = 20)
    private String telefone;

    @Email(message = "Informe um e-mail válido.")
    @Size(max = 120, message = "O email não pode ter mais que {max} caracteres.")
    @Column(length = 120)
    private String email;

    @OneToMany(mappedBy = "medico", cascade = CascadeType.ALL, orphanRemoval = true)
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
        this.nome = nome;
    }

    public String getCrm() {
        return crm;
    }

    public void setCrm(String crm) {
        this.crm = formatarCrm(crm);
    }

    /**
     * Padroniza o CRM como "CRM-UF 0000" a partir de qualquer forma digitada
     * ("to1001", "1001/TO", "crm-to 1001"...), para que o banco nunca guarde
     * o mesmo CRM em dois formatos. Se faltar a UF ou o número, devolve o texto
     * como veio e a validação o rejeita.
     */
    private static String formatarCrm(String crm) {
        if (crm == null)
            return null;

        String texto = crm.trim().toUpperCase(Locale.ROOT);
        String uf = texto.replaceAll("[^A-Z]", "").replaceFirst("^(CRM)+", "");
        String numero = texto.replaceAll("\\D", "").replaceFirst("^0+", "");

        if (uf.length() != 2 || numero.isEmpty() || numero.length() > 6)
            return texto;

        return "CRM-" + uf + " " + numero;
    }

    public String getEspecialidade() {
        return especialidade;
    }

    public void setEspecialidade(String especialidade) {
        this.especialidade = especialidade;
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

    public List<Consulta> getConsultas() {
        return consultas;
    }

    public void setConsultas(List<Consulta> consultas) {
        this.consultas = consultas;
    }
}
