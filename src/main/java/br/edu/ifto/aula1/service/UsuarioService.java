package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.entity.Usuario;
import br.edu.ifto.aula1.model.repository.PerfilRepository;
import br.edu.ifto.aula1.model.repository.UsuarioRepository;
import br.edu.ifto.aula1.security.TentativasLoginService;
import br.edu.ifto.aula1.validation.Credenciais;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PerfilRepository perfis;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final TentativasLoginService tentativas;
    private final TransactionTemplate transacao;

    // Sem caracteres que se confundem ao ditar ou copiar (0/O, 1/l/I).
    private static final String LETRAS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final int TAMANHO_PROVISORIA = 12;
    private static final SecureRandom SORTEIO = new SecureRandom();

    public UsuarioService(UsuarioRepository usuarios, PerfilRepository perfis, PasswordEncoder passwordEncoder,
                          AuditoriaService auditoria, TentativasLoginService tentativas,
                          PlatformTransactionManager transacoes) {
        this.transacao = new TransactionTemplate(transacoes);
        this.tentativas = tentativas;
        this.auditoria = auditoria;
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.passwordEncoder = passwordEncoder;
    }

    /** O que a tela de contas mostra; nunca o hash da senha. */
    public record Conta(Long id, String login, boolean ativo, boolean senhaProvisoria, List<String> perfis) {
    }

    public record SenhaProvisoria(String login, String senha) {

        /** Sem a senha, para que ela não vá parar em um log. */
        @Override
        public String toString() {
            return "SenhaProvisoria[login=" + login + "]";
        }
    }

    public enum ResultadoTroca {
        TROCADA, SENHA_INCORRETA, CONTA_BLOQUEADA
    }

    public Page<Conta> listarContas(String busca, int pagina, int tamanho) {
        return usuarios.findByLoginContainingIgnoreCase(busca == null ? "" : busca.strip(),
                        PageRequest.of(Math.max(pagina, 0), tamanho, Sort.by("login")))
                .map(u -> new Conta(u.getId(), u.getLogin(), u.isAtivo(), u.isTrocarSenha(),
                        u.getPerfis().stream().map(Perfil::getNome).sorted().toList()));
    }

    public boolean senhaProvisoria(String login) {
        return usuarios.existsByLoginAndTrocarSenhaTrue(login);
    }

    private record Tentativa(ResultadoTroca resultado, TentativasLoginService.Falha falha) {
    }

    /**
     * Troca da própria senha. Uma sessão deixada aberta não pode servir para descobrir a senha
     * por tentativa e erro: as falhas contam para o mesmo bloqueio do login.
     *
     * O BCrypt, lento, roda antes da transação, para que muitas trocas da mesma conta não
     * prendam as conexões dos outros. A decisão é tomada com a conta bloqueada (pessimista): se
     * a senha mudou nesse meio tempo, é conferida de novo, para não gravar por cima de uma
     * redefinição simultânea. A falha é contada nessa transação e auditada depois dela (por isso
     * NEVER), quando a conexão já foi devolvida.
     */
    @Transactional(propagation = Propagation.NEVER)
    public ResultadoTroca trocarSenha(String login, String senhaAtual, String novaSenha) {
        Optional<String> lido = transacao.execute(status -> tentativas.bloqueada(login)
                ? Optional.empty() : Optional.of(buscada(usuarios.findByLogin(login)).getPassword()));
        if (lido.isEmpty())
            return ResultadoTroca.CONTA_BLOQUEADA;
        String hashLido = lido.get();
        boolean confere = passwordEncoder.matches(senhaAtual, hashLido);
        String novoHash = confere ? passwordEncoder.encode(novaSenha) : null;

        Tentativa tentativa = transacao.execute(status -> {
            Usuario usuario = buscada(usuarios.buscarComBloqueio(login));
            if (tentativas.bloqueada(login))
                return new Tentativa(ResultadoTroca.CONTA_BLOQUEADA, null);
            boolean mesma = usuario.getPassword().equals(hashLido);
            if (!(mesma ? confere : passwordEncoder.matches(senhaAtual, usuario.getPassword())))
                return new Tentativa(ResultadoTroca.SENHA_INCORRETA, tentativas.contarFalha(login));

            usuario.trocarSenha(novoHash != null ? novoHash : passwordEncoder.encode(novaSenha));
            auditoria.registrar(AcaoAuditoria.SENHA_ALTERADA, "Usuario", usuario.getId(), null);
            return new Tentativa(ResultadoTroca.TROCADA, null);
        });
        if (tentativa.falha() != null)
            tentativas.auditarFalha(tentativa.falha());
        return tentativa.resultado();
    }

    private static Usuario buscada(Optional<Usuario> conta) {
        return conta.orElseThrow(() -> new RegistroNaoEncontradoException("Usuário não encontrado."));
    }

    /**
     * A administração redefine a senha de quem a esqueceu. A senha provisória é sorteada,
     * mostrada uma única vez e precisa ser trocada no próximo acesso.
     */
    @Transactional
    public SenhaProvisoria redefinirSenha(Long id, String administrador) {
        Usuario usuario = usuarios.buscarPorIdComBloqueio(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário não encontrado."));
        if (usuario.getLogin().equals(administrador))
            throw new RegraNegocioException("Para a própria conta, use \"Trocar senha\" em Meus dados.");

        String senha = sortearSenha();
        usuario.redefinirSenha(passwordEncoder.encode(senha));
        auditoria.registrar(AcaoAuditoria.SENHA_REDEFINIDA, "Usuario", usuario.getId(), "Conta " + usuario.getLogin());
        return new SenhaProvisoria(usuario.getLogin(), senha);
    }

    /** Dez letras e dois números (a política exige os dois), em ordem aleatória. */
    private static String sortearSenha() {
        List<Character> caracteres = new ArrayList<>();
        for (int i = 0; i < TAMANHO_PROVISORIA; i++) {
            String origem = i < 2 ? NUMEROS : LETRAS;
            caracteres.add(origem.charAt(SORTEIO.nextInt(origem.length())));
        }
        Collections.shuffle(caracteres, SORTEIO);
        StringBuilder senha = new StringBuilder();
        caracteres.forEach(senha::append);
        return senha.toString();
    }

    public boolean loginEmUso(String login) {
        return usuarios.existsByLogin(login);
    }

    /**
     * Cria a conta de acesso já com o papel inicial. A senha é gravada como hash BCrypt.
     * Os perfis são criados pelo InitialUsersInitializer ao subir a aplicação.
     */
    @Transactional
    public Usuario criar(Credenciais credenciais, String nomePerfil) {
        Perfil perfil = perfis.findByNome(nomePerfil)
                .orElseThrow(() -> new IllegalStateException("Perfil não cadastrado: " + nomePerfil));

        Usuario usuario = new Usuario(credenciais.getLogin(), passwordEncoder.encode(credenciais.getSenha()), true);
        usuario.adicionarPerfil(perfil);
        return usuarios.save(usuario);
    }
}
