package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.model.repository.PacienteRepository;
import br.edu.ifto.aula1.validation.Credenciais;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PacienteService {

    private final PacienteRepository repository;
    private final UsuarioService usuarioService;
    private final AgendamentoRepository agendamentoRepository;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public PacienteService(PacienteRepository repository, UsuarioService usuarioService,
                           AgendamentoRepository agendamentoRepository, AuditoriaService auditoria,
                           Clock clock) {
        this.repository = repository;
        this.usuarioService = usuarioService;
        this.agendamentoRepository = agendamentoRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    private static final Sort POR_NOME = Sort.by("nome").and(Sort.by("id"));

    /** Tamanho máximo do texto de busca; o excedente é descartado. */
    public static final int BUSCA_MAXIMA = 100;

    public List<Paciente> listar() {
        return repository.findAll(POR_NOME);
    }

    /** Pacientes por nome ou CPF, em ordem alfabética. Busca vazia lista todos. */
    public Page<Paciente> buscar(String texto, int pagina, int tamanho) {
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), tamanho, POR_NOME);
        String termo = texto == null ? "" : texto.strip();
        if (termo.length() > BUSCA_MAXIMA)
            termo = termo.substring(0, BUSCA_MAXIMA);
        if (termo.isEmpty())
            return repository.findAll(pageable);

        String digitos = termo.replaceAll("\\D", "");
        return repository.buscar("%" + escaparLike(termo.toLowerCase(Locale.ROOT)) + "%",
                digitos.isEmpty() ? null : "%" + digitos + "%", pageable);
    }

    /** O texto digitado é procurado literalmente: % e _ não funcionam como curingas. */
    private static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public Paciente buscar(Long id) {
        if (id == null)
            throw new RegistroNaoEncontradoException("Paciente não encontrado.");

        return repository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Paciente não encontrado."));
    }

    public boolean existe(Long id) {
        return id != null && repository.existsById(id);
    }

    /**
     * Indica se o CPF já pertence a outro paciente. Na edição, informe o id do
     * paciente editado para que o próprio CPF dele não conte como duplicado.
     */
    public boolean cpfEmUso(String cpf, Long idPacienteAtual) {
        return idPacienteAtual == null
                ? repository.existsByCpf(cpf)
                : repository.existsByCpfAndIdNot(cpf, idPacienteAtual);
    }

    /** Paciente dono da conta de acesso com este login, se houver. */
    public Optional<Paciente> buscarPorLogin(String login) {
        return repository.findByUsuarioLogin(login);
    }

    /**
     * Cadastra o paciente junto com a conta de acesso dele, na mesma transação:
     * se um dos dois falhar, nenhum é gravado. O papel inicial é PACIENTE.
     * LGPD: sem o aceite da política de privacidade, o cadastro não é feito; o momento do
     * aceite fica registrado no paciente.
     */
    @Transactional
    public Paciente cadastrar(Paciente paciente, Credenciais credenciais, boolean consentimento) {
        if (!consentimento)
            throw new RegraNegocioException("consentimento", CONSENTIMENTO_OBRIGATORIO);

        paciente.setId(null);
        paciente.vincularUsuario(usuarioService.criar(credenciais, Perfil.PACIENTE));
        paciente.registrarConsentimento(LocalDateTime.now(clock));
        Paciente salvo = repository.save(paciente);
        auditoria.registrar(AcaoAuditoria.PACIENTE_CADASTRADO, "Paciente", salvo.getId(),
                "Conta " + credenciais.getLogin());
        return salvo;
    }

    public static final String CONSENTIMENTO_OBRIGATORIO =
            "É preciso aceitar a política de privacidade para concluir o cadastro.";

    /**
     * Copia os dados do formulário para o paciente do banco. Não é preciso chamar save():
     * dentro da transação a entidade está gerenciada e as alterações são gravadas no commit.
     */
    @Transactional
    public Paciente atualizar(Paciente dados) {
        Paciente paciente = buscar(dados.getId());

        paciente.setNome(dados.getNome());
        paciente.setCpf(dados.getCpf());
        paciente.setDataNascimento(dados.getDataNascimento());
        paciente.setTelefone(dados.getTelefone());
        paciente.setEmail(dados.getEmail());

        auditoria.registrar(AcaoAuditoria.PACIENTE_ALTERADO, "Paciente", paciente.getId(), null);
        return paciente;
    }

    /**
     * Remove o paciente e, pelo cascade da entidade, a conta de acesso dele. Um paciente com
     * agendamentos (mesmo cancelados) não é removido: isso apagaria o histórico da clínica.
     */
    @Transactional
    public void remover(Long id) {
        Paciente paciente = buscar(id);
        if (agendamentoRepository.existsByPacienteId(id))
            throw new RegraNegocioException("Não é possível excluir um paciente que tem agendamentos registrados.");

        repository.delete(paciente);
        auditoria.registrar(AcaoAuditoria.PACIENTE_EXCLUIDO, "Paciente", id, null);
    }
}
