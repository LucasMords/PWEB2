package br.edu.ifto.aula1.model.entity;

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

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Só grava as colunas alteradas: um login simultâneo (contagem de falhas) não desfaz a troca de senha.
@Entity
@DynamicUpdate
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

    // Proteção contra tentativa e erro de senha: depois de algumas falhas seguidas, a conta
    // fica bloqueada por um tempo (ver TentativasLoginService). O default no banco permite
    // que os dados de demonstração (db/demo) insiram contas sem informar estas colunas.
    @ColumnDefault("0")
    @Column(name = "tentativas_falhas", nullable = false)
    private int tentativasFalhas = 0;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    /** Senha provisória, definida pela administração: precisa ser trocada no próximo acesso. */
    @ColumnDefault("false")
    @Column(name = "trocar_senha", nullable = false)
    private boolean trocarSenha = false;

    // Em lote: a listagem paginada de contas não faz uma consulta por conta.
    @BatchSize(size = 20)
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

    /** O dono, que acertou a senha atual, escolheu uma nova (já como hash); esquece as falhas anteriores. */
    public void trocarSenha(String hash) {
        this.password = hash;
        this.trocarSenha = false;
        registrarSucesso();
    }

    /** A administração definiu uma senha provisória (já como hash); desfaz o bloqueio por tentativas. */
    public void redefinirSenha(String hash) {
        this.password = hash;
        this.trocarSenha = true;
        registrarSucesso();
    }

    public boolean isTrocarSenha() {
        return trocarSenha;
    }

    public boolean isBloqueadoEm(LocalDateTime agora) {
        return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
    }

    /**
     * Conta uma senha errada. Ao atingir o limite, bloqueia a conta até agora + duração e
     * zera o contador (o próximo ciclo recomeça depois do bloqueio). Devolve true se bloqueou.
     */
    public boolean registrarFalha(LocalDateTime agora, int limite, Duration duracao) {
        tentativasFalhas++;
        if (tentativasFalhas < limite)
            return false;
        tentativasFalhas = 0;
        bloqueadoAte = agora.plus(duracao);
        return true;
    }

    /** Login bem-sucedido: esquece as falhas anteriores. Devolve true se havia algo a limpar. */
    public boolean registrarSucesso() {
        boolean havia = tentativasFalhas > 0 || bloqueadoAte != null;
        tentativasFalhas = 0;
        bloqueadoAte = null;
        return havia;
    }

    public int getTentativasFalhas() { return tentativasFalhas; }
    public LocalDateTime getBloqueadoAte() { return bloqueadoAte; }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public boolean isAtivo() { return ativo; }
    public List<Perfil> getPerfis() { return perfis; }

    public void setLogin(String login) { this.login = login; }
    public void setPassword(String password) { this.password = password; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
