package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Consulta;
import br.edu.ifto.aula1.model.repository.ConsultaRepository;
import br.edu.ifto.aula1.model.entity.Medico;
import org.springframework.security.access.AccessDeniedException;
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
    private final MedicoService medicoService;

    public ConsultaService(ConsultaRepository repository, AuditoriaService auditoria, MedicoService medicoService) {
        this.medicoService = medicoService;
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

    /** Id do médico dono da conta, ou null: só as consultas dele mostram as observações na listagem. */
    public Long medicoDaConta(String login) {
        return medicoService.buscarPorLogin(login).map(Medico::getId).orElse(null);
    }

    /** As observações são dado de saúde: só o médico que atendeu abre a consulta para edição. */
    public Consulta buscarDoMedico(Long id, String login) {
        Consulta consulta = buscar(id);
        if (!consulta.getMedico().getId().equals(medicoDaConta(login)))
            throw new AccessDeniedException("A consulta é de outro médico.");
        return consulta;
    }

    /**
     * Não é preciso chamar save(): dentro da transação a entidade está gerenciada e a
     * alteração é gravada no commit.
     */
    @Transactional
    public Consulta atualizarObservacoes(Long id, String observacoes, String login) {
        Consulta consulta = buscarDoMedico(id, login);
        consulta.setObservacoes(observacoes == null || observacoes.isBlank() ? null : observacoes.trim());
        // O texto das observações é dado de saúde: a auditoria registra só que houve alteração.
        auditoria.registrar(AcaoAuditoria.CONSULTA_ALTERADA, "Consulta", consulta.getId(), null);
        return consulta;
    }
}
