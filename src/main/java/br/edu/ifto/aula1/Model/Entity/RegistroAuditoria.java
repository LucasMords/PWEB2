package br.edu.ifto.aula1.Model.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Quem fez o quê, quando e em qual registro. Só é inserido, nunca alterado nem excluído
 * pela aplicação (não há setters). Guarda o login, e não uma referência ao Usuario, para
 * que o histórico sobreviva à exclusão da conta. Dados de saúde não entram no detalhe:
 * ele identifica o registro (ids, datas), não o conteúdo do atendimento.
 */
@Entity
@Table(name = "auditoria", indexes = {
        @Index(name = "ix_auditoria_data_hora", columnList = "data_hora"),
        @Index(name = "ix_auditoria_usuario", columnList = "usuario")})
public class RegistroAuditoria {

    public static final int DETALHE_MAXIMO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false, updatable = false, length = 80)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private AcaoAuditoria acao;

    @Column(updatable = false, length = 40)
    private String entidade;

    @Column(name = "entidade_id", updatable = false)
    private Long entidadeId;

    @Column(updatable = false, length = DETALHE_MAXIMO)
    private String detalhe;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(LocalDateTime dataHora, String usuario, AcaoAuditoria acao,
                             String entidade, Long entidadeId, String detalhe) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhe = detalhe == null || detalhe.length() <= DETALHE_MAXIMO
                ? detalhe : detalhe.substring(0, DETALHE_MAXIMO);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public AcaoAuditoria getAcao() {
        return acao;
    }

    public String getEntidade() {
        return entidade;
    }

    public Long getEntidadeId() {
        return entidadeId;
    }

    public String getDetalhe() {
        return detalhe;
    }
}
