package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.repository.MedicoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MedicoService {

    private final MedicoRepository repository;

    public MedicoService(MedicoRepository repository) {
        this.repository = repository;
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

    @Transactional
    public Medico cadastrar(Medico medico) {
        medico.setId(null);
        return repository.save(medico);
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

        return medico;
    }

    /** Remove o médico e, pelo cascade da entidade, as consultas dele. */
    @Transactional
    public void remover(Long id) {
        repository.delete(buscar(id));
    }
}
