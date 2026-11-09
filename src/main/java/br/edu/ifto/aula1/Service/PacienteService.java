package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.repository.PacienteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PacienteService {

    private final PacienteRepository repository;

    public PacienteService(PacienteRepository repository) {
        this.repository = repository;
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

    @Transactional
    public Paciente cadastrar(Paciente paciente) {
        paciente.setId(null);
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

    /** Remove o paciente e, pelo cascade da entidade, as consultas dele. */
    @Transactional
    public void remover(Long id) {
        repository.delete(buscar(id));
    }
}
