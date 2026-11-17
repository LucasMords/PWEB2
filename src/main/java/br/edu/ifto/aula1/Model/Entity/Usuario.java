package br.edu.ifto.aula1.Model.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String login;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean ativo = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "usuario_perfil",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "perfil_id"))
    private List<Perfil> perfis = new ArrayList<>();

    protected Usuario() {
    }

    public Usuario(String login, String password, boolean ativo) {
        this.login = login;
        this.password = password;
        this.ativo = ativo;
    }

    public void adicionarPerfil(Perfil perfil) {
        if (!perfis.contains(perfil)) {
            perfis.add(perfil);
            perfil.adicionarUsuario(this);
        }
    }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public boolean isAtivo() { return ativo; }
    public List<Perfil> getPerfis() { return perfis; }

    public void setLogin(String login) { this.login = login; }
    public void setPassword(String password) { this.password = password; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
