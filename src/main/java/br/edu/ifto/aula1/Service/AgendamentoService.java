package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Dto.AgendamentoFiltro;
import br.edu.ifto.aula1.Model.Entity.Agenda;
import br.edu.ifto.aula1.Model.Entity.Agendamento;
import br.edu.ifto.aula1.Model.Entity.Consulta;
import br.edu.ifto.aula1.Model.Entity.Paciente;
import br.edu.ifto.aula1.Model.Entity.StatusAgendamento;
import br.edu.ifto.aula1.Model.repository.AgendaRepository;
import br.edu.ifto.aula1.Model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.Model.repository.ConsultaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Regras de agendamento, cancelamento e confirmação de presença.
 *
 * Concorrência: cada operação de escrita começa lendo, com bloqueio pessimista
 * (SELECT ... FOR UPDATE), o registro que decide a regra: a Agenda ao reservar, o
 * Agendamento ao cancelar ou confirmar. Quem chega depois espera o commit de quem chegou
 * primeiro e então lê o estado já atualizado, de modo que a verificação de status feita
 * aqui vale até o fim da transação. As restrições únicas do banco (um agendamento ativo
 * por horário, uma consulta por agendamento) ficam como segunda barreira.
 */
@Service
@Transactional(readOnly = true)
public class AgendamentoService {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoService.class);

    private final AgendamentoRepository repository;
    private final AgendaRepository agendaRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteService pacienteService;

    public AgendamentoService(AgendamentoRepository repository, AgendaRepository agendaRepository,
                              ConsultaRepository consultaRepository, PacienteService pacienteService) {
        this.repository = repository;
        this.agendaRepository = agendaRepository;
        this.consultaRepository = consultaRepository;
        this.pacienteService = pacienteService;
    }

    public Page<Agendamento> listar(AgendamentoFiltro filtro, Pageable pageable) {
        return repository.buscar(filtro.getPacienteId(), filtro.getMedicoId(), filtro.getData(),
                filtro.getStatus(), pageable);
    }

    public Page<Agendamento> listarPorPaciente(Long pacienteId, Pageable pageable) {
        return repository.buscar(pacienteId, null, null, null, pageable);
    }

    public Page<Agendamento> listarPorMedico(Long medicoId, Pageable pageable) {
        return repository.buscar(null, medicoId, null, null, pageable);
    }

    /**
     * Reserva o horário para o paciente. Agendamento e Agenda são gravados na mesma
     * transação: ou os dois mudam, ou nenhum. Nenhuma Consulta é criada aqui.
     */
    @Transactional
    public Agendamento agendar(Long agendaId, Long pacienteId) {
        Paciente paciente = pacienteService.buscar(pacienteId);
        Agenda agenda = bloquearAgenda(agendaId);

        // A disponibilidade exibida na tela pode estar desatualizada: vale o que está no banco agora.
        if (!agenda.isDisponivel()
                || repository.existsByAgendaIdAndStatusNot(agenda.getId(), StatusAgendamento.CANCELADO))
            throw new RegraNegocioException(AgendaService.HORARIO_INDISPONIVEL);

        LocalDateTime agora = LocalDateTime.now();
        if (!agenda.getDataHora().isAfter(agora))
            throw new RegraNegocioException("Não é possível agendar um horário que já passou.");

        agenda.reservar();
        try {
            // O flush antecipa o INSERT para que a restrição única do banco seja conferida aqui.
            return repository.saveAndFlush(new Agendamento(agenda, paciente, agora));
        } catch (DataIntegrityViolationException e) {
            log.warn("Reserva duplicada do horário {} barrada pelo banco", agendaId);
            throw new RegraNegocioException(AgendaService.HORARIO_INDISPONIVEL);
        }
    }

    /** Cancelamento feito pela equipe da clínica. */
    @Transactional
    public void cancelar(Long id) {
        cancelar(bloquearAgendamento(id));
    }

    /**
     * Cancelamento feito pelo próprio paciente. Se o agendamento for de outra pessoa, a
     * resposta é a mesma de um agendamento inexistente, para não revelar que ele existe.
     */
    @Transactional
    public void cancelarDoPaciente(Long id, Long pacienteId) {
        Agendamento agendamento = bloquearAgendamento(id);

        if (!agendamento.getPaciente().getId().equals(pacienteId)) {
            log.warn("Paciente {} tentou cancelar o agendamento {}, que não é dele", pacienteId, id);
            throw new RegistroNaoEncontradoException("Agendamento não encontrado.");
        }

        cancelar(agendamento);
    }

    /** O registro não é excluído: fica CANCELADO, como histórico, e o horário volta a ficar livre. */
    private void cancelar(Agendamento agendamento) {
        switch (agendamento.getStatus()) {
            case REALIZADO -> throw new RegraNegocioException("Não é possível cancelar um agendamento já realizado.");
            case CANCELADO -> throw new RegraNegocioException("Este agendamento já foi cancelado.");
            case AGENDADO -> {
                agendamento.cancelar();
                agendamento.getAgenda().liberar();
                // Entidades gerenciadas: as duas alterações são gravadas juntas no commit.
            }
        }
    }

    /**
     * Confirma que o paciente compareceu: cria a Consulta e marca o agendamento como
     * REALIZADO, na mesma transação. Uma segunda tentativa é rejeitada, sem criar outra consulta.
     */
    @Transactional
    public Consulta confirmarPresenca(Long id) {
        Agendamento agendamento = bloquearAgendamento(id);

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO)
            throw new RegraNegocioException("Não é possível confirmar a presença em um agendamento cancelado.");
        if (agendamento.getStatus() == StatusAgendamento.REALIZADO
                || consultaRepository.existsByAgendamentoId(agendamento.getId()))
            throw new RegraNegocioException("A presença deste agendamento já foi confirmada.");

        agendamento.marcarRealizado();
        try {
            return consultaRepository.saveAndFlush(new Consulta(agendamento, LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            log.warn("Consulta duplicada para o agendamento {} barrada pelo banco", id);
            throw new RegraNegocioException("A presença deste agendamento já foi confirmada.");
        }
    }

    private Agenda bloquearAgenda(Long id) {
        if (id == null)
            throw new RegistroNaoEncontradoException("Horário não encontrado.");

        try {
            return agendaRepository.buscarComBloqueio(id)
                    .orElseThrow(() -> new RegistroNaoEncontradoException("Horário não encontrado."));
        } catch (ConcurrencyFailureException e) {
            // Outra transação segurou o horário além do tempo de espera do bloqueio.
            log.warn("Tempo de espera esgotado ao bloquear o horário {}", id);
            throw new RegraNegocioException(AgendaService.HORARIO_INDISPONIVEL);
        }
    }

    private Agendamento bloquearAgendamento(Long id) {
        if (id == null)
            throw new RegistroNaoEncontradoException("Agendamento não encontrado.");

        try {
            return repository.buscarComBloqueio(id)
                    .orElseThrow(() -> new RegistroNaoEncontradoException("Agendamento não encontrado."));
        } catch (ConcurrencyFailureException e) {
            log.warn("Tempo de espera esgotado ao bloquear o agendamento {}", id);
            throw new RegraNegocioException("O agendamento está sendo alterado por outra pessoa. Tente novamente.");
        }
    }
}
