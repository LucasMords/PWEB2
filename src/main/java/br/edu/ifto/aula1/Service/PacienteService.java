package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.Perfil;
import br.edu.ifto.aula1.Model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.Model.repository.PacienteRepository;
import br.edu.ifto.aula1.Validation.Credenciais;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PacienteService {

    private final PacienteRepository repository;
    private final UsuarioService usuarioService;
    private final AgendamentoRepository agendamentoRepository;

    public PacienteService(PacienteRepository repository, UsuarioService usuarioService,
                           AgendamentoRepository agendamentoRepository) {
        this.repository = repository;
        this.usuarioService = usuarioService;
        this.agendamentoRepository = agendamentoRepository;
    }

    public List<Paciente> listar() {
        return repository.findAll(Sort.by("nome").and(Sort.by("id")));
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
     */
    @Transactional
    public Paciente cadastrar(Paciente paciente, Credenciais credenciais) {
        paciente.setId(null);
        paciente.vincularUsuario(usuarioService.criar(credenciais, Perfil.PACIENTE));
        return repository.save(paciente);
    }

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
    }
}
