package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Medico;
import br.edu.ifto.aula1.model.entity.Perfil;
import br.edu.ifto.aula1.model.repository.AgendaRepository;
import br.edu.ifto.aula1.model.repository.MedicoRepository;
import br.edu.ifto.aula1.validation.Credenciais;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MedicoService {

    private final MedicoRepository repository;
    private final UsuarioService usuarioService;
    private final AgendaRepository agendaRepository;
    private final AuditoriaService auditoria;

    public MedicoService(MedicoRepository repository, UsuarioService usuarioService,
                         AgendaRepository agendaRepository, AuditoriaService auditoria) {
        this.auditoria = auditoria;
        this.repository = repository;
        this.usuarioService = usuarioService;
        this.agendaRepository = agendaRepository;
    }

    public List<Medico> listar() {
        return repository.findAll(Sort.by("nome").and(Sort.by("id")));
    }

    public Medico buscar(Long id) {
        if (id == null)
            throw new RegistroNaoEncontradoException("Médico não encontrado.");

        return repository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Médico não encontrado."));
    }

    /** Bloqueia o médico até o fim da transação de quem chama: serializa as mudanças na agenda dele. */
    @Transactional
    public void bloquear(Long id) {
        try {
            repository.buscarComBloqueio(id)
                    .orElseThrow(() -> new RegistroNaoEncontradoException("Médico não encontrado."));
        } catch (ConcurrencyFailureException e) {
            throw new RegraNegocioException("A agenda deste médico está sendo alterada. Tente novamente.");
        }
    }

    public boolean existe(Long id) {
        return id != null && repository.existsById(id);
    }

    /**
     * Indica se o CRM já pertence a outro médico. Na edição, informe o id do
     * médico editado para que o próprio CRM dele não conte como duplicado.
     */
    public boolean crmEmUso(String crm, Long idMedicoAtual) {
        return idMedicoAtual == null
                ? repository.existsByCrm(crm)
                : repository.existsByCrmAndIdNot(crm, idMedicoAtual);
    }

    /** Médico dono da conta de acesso com este login, se houver. */
    public Optional<Medico> buscarPorLogin(String login) {
        return repository.findByUsuarioLogin(login);
    }

    /**
     * Cadastra o médico junto com a conta institucional dele, na mesma transação:
     * se um dos dois falhar, nenhum é gravado. O papel inicial é MEDICO.
     */
    @Transactional
    public Medico cadastrar(Medico medico, Credenciais credenciais) {
        medico.setId(null);
        medico.vincularUsuario(usuarioService.criar(credenciais, Perfil.MEDICO));
        Medico salvo = repository.save(medico);
        auditoria.registrar(AcaoAuditoria.MEDICO_CADASTRADO, "Medico", salvo.getId(), "Conta " + credenciais.getLogin());
        return salvo;
    }

    /**
     * Copia os dados do formulário para o médico do banco. Não é preciso chamar save():
     * dentro da transação a entidade está gerenciada e as alterações são gravadas no commit.
     */
    @Transactional
    public Medico atualizar(Medico dados) {
        Medico medico = buscar(dados.getId());

        medico.setNome(dados.getNome());
        medico.setCrm(dados.getCrm());
        medico.setEspecialidade(dados.getEspecialidade());
        medico.setTelefone(dados.getTelefone());
        medico.setEmail(dados.getEmail());
        auditoria.registrar(AcaoAuditoria.MEDICO_ALTERADO, "Medico", medico.getId(), null);

        return medico;
    }

    /**
     * Remove o médico e, pelo cascade da entidade, a conta de acesso dele. Um médico com
     * horários na agenda não é removido: isso apagaria o histórico de agendamentos e consultas.
     */
    @Transactional
    public void remover(Long id) {
        Medico medico = buscar(id);
        if (agendaRepository.existsByMedicoId(id))
            throw new RegraNegocioException("Não é possível excluir um médico que tem horários na agenda.");

        repository.delete(medico);
        auditoria.registrar(AcaoAuditoria.MEDICO_EXCLUIDO, "Medico", id, null);
    }

    /** Especialidades dos médicos cadastrados, em ordem alfabética. */
    public List<String> listarEspecialidades() {
        return repository.buscarEspecialidades();
    }
}
