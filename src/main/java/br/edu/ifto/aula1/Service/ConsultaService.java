package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Model.Entity.AcaoAuditoria;
import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Histórico das consultas realizadas. Não há cadastro nem exclusão aqui: a consulta
 * nasce em AgendamentoService.confirmarPresenca e fica como registro do atendimento.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaService {

    private final ConsultaRepository repository;
    private final AuditoriaService auditoria;

    public ConsultaService(ConsultaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    public List<Consulta> listar() {
        return repository.findAllByOrderByDataRealizacaoDesc();
    }

    public List<Consulta> listarPorPaciente(Long pacienteId) {
        return repository.findByAgendamentoPacienteIdOrderByDataRealizacaoDesc(pacienteId);
    }

    public List<Consulta> listarPorMedico(Long medicoId) {
        return repository.findByAgendamentoAgendaMedicoIdOrderByDataRealizacaoDesc(medicoId);
    }

    /** Consulta com agendamento, paciente e médico carregados, para exibição. */
    public Consulta buscar(Long id) {
        if (id == null)
            throw new RegistroNaoEncontradoException("Consulta não encontrada.");

        return repository.findDetalhadaById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Consulta não encontrada."));
    }

    /**
     * Não é preciso chamar save(): dentro da transação a entidade está gerenciada e a
     * alteração é gravada no commit.
     */
    @Transactional
    public Consulta atualizarObservacoes(Long id, String observacoes) {
        Consulta consulta = buscar(id);
        consulta.setObservacoes(observacoes == null || observacoes.isBlank() ? null : observacoes.trim());
        // O texto das observações é dado de saúde: a auditoria registra só que houve alteração.
        auditoria.registrar(AcaoAuditoria.CONSULTA_ALTERADA, "Consulta", consulta.getId(), null);
        return consulta;
    }
}
