package br.edu.ifto.aula1.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "perfis")
public class Perfil {

    // Nomes gravados no banco, sem o prefixo ROLE_ (o Spring Security o acrescenta).
    public static final String ADMIN = "ADMIN";       // administração da clínica
    public static final String ATENDENTE = "ATENDENTE"; // secretaria/recepção: marca as consultas
    public static final String MEDICO = "MEDICO";     // conta institucional criada pelo ADMIN
    public static final String PACIENTE = "PACIENTE"; // papel inicial de quem se cadastra sozinho

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String nome;

    @ManyToMany(mappedBy = "perfis", fetch = FetchType.LAZY)
    private List<Usuario> usuarios = new ArrayList<>();

    protected Perfil() {
    }

    public Perfil(String nome) {
        this.nome = nome;
    }

    void adicionarUsuario(Usuario usuario) {
        if (!usuarios.contains(usuario)) usuarios.add(usuario);
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public List<Usuario> getUsuarios() { return usuarios; }
}
