package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.StatusConsulta;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsultaService {

    private final ConsultaRepository repository;
    private final PacienteService pacienteService;
    private final MedicoService medicoService;

    public ConsultaService(ConsultaRepository repository, PacienteService pacienteService, MedicoService medicoService) {
        this.repository = repository;
        this.pacienteService = pacienteService;
        this.medicoService = medicoService;
    }

    public List<Consulta> listar() {
        return repository.findAllByOrderByDataHoraDesc();
    }

    public List<Consulta> listarPorPaciente(Long pacienteId) {
        return repository.findByPacienteIdOrderByDataHoraDesc(pacienteId);
    }

    public List<Consulta> listarPorMedico(Long medicoId) {
        return repository.findByMedicoIdOrderByDataHoraDesc(medicoId);
    }

    public Consulta buscar(Long id) {
        if (id == null)
            throw new RegistroNaoEncontradoException("Consulta não encontrada.");

        return repository.findById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Consulta não encontrada."));
    }

    /**
     * Toda consulta nova começa agendada, mesmo que a requisição traga outro status.
     * Paciente e médico do formulário só têm o id; aqui são trocados pelos registros do banco.
     */
    @Transactional
    public Consulta agendar(Consulta consulta) {
        consulta.setId(null);
        consulta.setStatus(StatusConsulta.AGENDADA);
        consulta.setPaciente(pacienteService.buscar(consulta.getPaciente().getId()));
        consulta.setMedico(medicoService.buscar(consulta.getMedico().getId()));
        return repository.save(consulta);
    }

    /**
     * Copia os dados do formulário para a consulta do banco. Não é preciso chamar save():
     * dentro da transação a entidade está gerenciada e as alterações são gravadas no commit.
     */
    @Transactional
    public Consulta atualizar(Consulta dados) {
        Consulta consulta = buscar(dados.getId());

        consulta.setPaciente(pacienteService.buscar(dados.getPaciente().getId()));
        consulta.setMedico(medicoService.buscar(dados.getMedico().getId()));
        consulta.setDataHora(dados.getDataHora());
        consulta.setStatus(dados.getStatus());
        consulta.setObservacoes(dados.getObservacoes());

        return consulta;
    }

    @Transactional
    public void remover(Long id) {
        repository.delete(buscar(id));
    }
}
