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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Regras de agendamento, cancelamento, confirmação de presença e registro de falta.
 *
 * Concorrência: cada operação de escrita começa lendo, com bloqueio pessimista
 * (SELECT ... FOR UPDATE), o registro que decide a regra: a Agenda ao reservar, o
 * Agendamento ao cancelar, confirmar ou registrar falta. Quem chega depois espera o commit
 * de quem chegou primeiro e então lê o estado já atualizado, de modo que a verificação de
 * status feita aqui vale até o fim da transação. As restrições únicas do banco (um
 * agendamento ativo por horário, uma consulta por agendamento) ficam como segunda barreira.
 */
@Service
@Transactional(readOnly = true)
public class AgendamentoService {

    static final String PRESENCA_JA_CONFIRMADA = "A presença deste agendamento já foi confirmada.";
    static final String FALTA_JA_REGISTRADA = "A falta deste agendamento já foi registrada.";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final Logger log = LoggerFactory.getLogger(AgendamentoService.class);

    private final AgendamentoRepository repository;
    private final AgendaRepository agendaRepository;
    private final ConsultaRepository consultaRepository;
    private final PacienteService pacienteService;
    private final Clock clock;

    public AgendamentoService(AgendamentoRepository repository, AgendaRepository agendaRepository,
                              ConsultaRepository consultaRepository, PacienteService pacienteService,
                              Clock clock) {
        this.repository = repository;
        this.agendaRepository = agendaRepository;
        this.consultaRepository = consultaRepository;
        this.pacienteService = pacienteService;
        this.clock = clock;
    }

    /** O instante que as telas usam para habilitar ou desabilitar as ações. */
    public LocalDateTime agora() {
        return LocalDateTime.now(clock);
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
        exigirLivre(agenda);
        return reservar(agenda, paciente, agora());
    }

    /**
     * Agendamento com horário, médico e paciente carregados, para a tela de reagendamento.
     * Com "pacienteRestrito" (o paciente autenticado), o de outra pessoa é tratado como inexistente.
     */
    public Agendamento buscarDetalhado(Long id, Long pacienteRestrito) {
        Agendamento agendamento = (id == null ? Optional.<Agendamento>empty() : repository.findDetalhadoById(id))
                .orElseThrow(() -> new RegistroNaoEncontradoException("Agendamento não encontrado."));
        exigirDono(agendamento, pacienteRestrito);
        return agendamento;
    }

    /**
     * Agendamento para a tela de reagendamento, já conferido: precisa estar AGENDADO e, para
     * o paciente, ser dele e estar dentro do prazo. A conferência se repete em reagendar().
     */
    public Agendamento buscarParaReagendar(Long id, Long pacienteRestrito) {
        Agendamento agendamento = buscarDetalhado(id, pacienteRestrito);
        exigirAgendado(agendamento, Acao.REAGENDAR);
        if (pacienteRestrito != null)
            exigirPrazoDoPaciente(agendamento, "reagendar");
        return agendamento;
    }

    /**
     * Cancelamento. "pacienteRestrito" é o id do paciente autenticado (o paciente só cancela
     * os próprios agendamentos, até 24 horas antes) ou null quando quem cancela é a equipe,
     * que pode cancelar a qualquer momento (ex.: o médico não pôde atender). "por" é o login
     * de quem cancelou; o motivo é opcional.
     */
    @Transactional
    public void cancelar(Long id, Long pacienteRestrito, String por, String motivo) {
        String motivoTratado = tratarMotivo(motivo);
        Agendamento agendamento = bloquearAgendamento(id);
        exigirDono(agendamento, pacienteRestrito);
        exigirAgendado(agendamento, Acao.CANCELAR);
        if (pacienteRestrito != null)
            exigirPrazoDoPaciente(agendamento, "cancelar");

        cancelar(agendamento, por, motivoTratado);
    }

    /**
     * Troca o horário de um agendamento por outro horário livre do mesmo médico, numa única
     * transação: o agendamento atual fica CANCELADO (com o motivo "Reagendado para ...") e
     * nasce um novo, AGENDADO, para o mesmo paciente. Se o novo horário não estiver livre,
     * nada muda. As regras de quem pode são as do cancelamento.
     */
    @Transactional
    public Agendamento reagendar(Long id, Long novaAgendaId, Long pacienteRestrito, String por) {
        Agendamento atual = bloquearAgendamento(id);
        exigirDono(atual, pacienteRestrito);
        exigirAgendado(atual, Acao.REAGENDAR);
        if (pacienteRestrito != null)
            exigirPrazoDoPaciente(atual, "reagendar");

        if (novaAgendaId != null && Objects.equals(atual.getAgenda().getId(), novaAgendaId))
            throw new RegraNegocioException("Escolha um horário diferente do atual.");

        Agenda nova = bloquearAgenda(novaAgendaId);
        if (!Objects.equals(nova.getMedico().getId(), atual.getAgenda().getMedico().getId()))
            throw new RegraNegocioException("O reagendamento deve ser com o mesmo médico. "
                    + "Para trocar de médico, cancele e faça um novo agendamento.");
        exigirLivre(nova);
        LocalDateTime agora = agora();
        if (!nova.getDataHora().isAfter(agora))
            throw new RegraNegocioException("Não é possível agendar um horário que já passou.");

        // Só depois de tudo conferido: nenhuma exceção abaixo deixa o agendamento atual pela metade.
        cancelar(atual, por, "Reagendado para " + nova.getData().format(DATA) + " às " + nova.getHorario().format(HORA) + ".");
        return reservar(nova, atual.getPaciente(), agora);
    }

    /** Horário já bloqueado e conferido como livre: confere que é futuro, reserva e grava o agendamento. */
    private Agendamento reservar(Agenda agenda, Paciente paciente, LocalDateTime agora) {
        if (!agenda.getDataHora().isAfter(agora))
            throw new RegraNegocioException("Não é possível agendar um horário que já passou.");

        agenda.reservar();
        try {
            // O flush antecipa o INSERT para que a restrição única do banco seja conferida aqui.
            return repository.saveAndFlush(new Agendamento(agenda, paciente, agora));
        } catch (DataIntegrityViolationException e) {
            log.warn("Reserva duplicada do horário {} barrada pelo banco", agenda.getId());
            throw new RegraNegocioException(AgendaService.HORARIO_INDISPONIVEL);
        }
    }

    /** A disponibilidade exibida na tela pode estar desatualizada: vale o que está no banco agora. */
    private void exigirLivre(Agenda agenda) {
        if (!agenda.isDisponivel()
                || repository.existsByAgendaIdAndStatusNot(agenda.getId(), StatusAgendamento.CANCELADO))
            throw new RegraNegocioException(AgendaService.HORARIO_INDISPONIVEL);
    }

    /** O registro não é excluído: fica CANCELADO, como histórico, e o horário volta a ficar livre. */
    private void cancelar(Agendamento agendamento, String por, String motivo) {
        agendamento.cancelar(agora(), por, motivo);
        agendamento.getAgenda().liberar();
        // Entidades gerenciadas: as duas alterações são gravadas juntas no commit.
    }

    /**
     * Para o paciente, o agendamento de outra pessoa é tratado como inexistente, para não
     * revelar que ele existe. Para a equipe (pacienteRestrito null), não há restrição.
     */
    private static void exigirDono(Agendamento agendamento, Long pacienteRestrito) {
        if (pacienteRestrito != null && !agendamento.getPaciente().getId().equals(pacienteRestrito)) {
            log.warn("Paciente {} tentou alterar o agendamento {}, que não é dele", pacienteRestrito, agendamento.getId());
            throw new RegistroNaoEncontradoException("Agendamento não encontrado.");
        }
    }

    private void exigirPrazoDoPaciente(Agendamento agendamento, String acao) {
        if (!agendamento.permiteCancelamentoPeloPaciente(agora()))
            throw new RegraNegocioException("Só é possível " + acao + " pelo sistema até "
                    + Agendamento.PRAZO_CANCELAMENTO_PACIENTE.toHours()
                    + " horas antes do horário. Entre em contato com a clínica.");
    }

    /** Motivo em branco vira null; acima do tamanho da coluna é rejeitado (não truncado). */
    private static String tratarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank())
            return null;
        String limpo = motivo.strip();
        if (limpo.length() > Agendamento.MOTIVO_MAXIMO)
            throw new RegraNegocioException("O motivo deve ter no máximo " + Agendamento.MOTIVO_MAXIMO + " caracteres.");
        return limpo;
    }

    /**
     * Confirma que o paciente compareceu: cria a Consulta e marca o agendamento como
     * REALIZADO, na mesma transação. Só vale a partir de 1 hora antes do horário. Uma
     * segunda tentativa é rejeitada, sem criar outra consulta.
     */
    @Transactional
    public Consulta confirmarPresenca(Long id) {
        Agendamento agendamento = bloquearAgendamento(id);

        if (agendamento.getStatus() == StatusAgendamento.AGENDADO
                && consultaRepository.existsByAgendamentoId(agendamento.getId()))
            throw new RegraNegocioException(PRESENCA_JA_CONFIRMADA);
        exigirAgendado(agendamento, Acao.CONFIRMAR_PRESENCA);

        LocalDateTime agora = agora();
        if (!agendamento.permiteConfirmarPresenca(agora))
            throw new RegraNegocioException("A presença só pode ser confirmada a partir de "
                    + Agendamento.ANTECEDENCIA_CONFIRMACAO.toHours() + " hora antes do horário da consulta.");

        agendamento.marcarRealizado();
        try {
            return consultaRepository.saveAndFlush(new Consulta(agendamento, agora));
        } catch (DataIntegrityViolationException e) {
            log.warn("Consulta duplicada para o agendamento {} barrada pelo banco", id);
            throw new RegraNegocioException(PRESENCA_JA_CONFIRMADA);
        }
    }

    /**
     * Registra que o paciente não compareceu. O agendamento fica como FALTOU, nenhuma
     * consulta é criada e o horário (que já passou) continua ocupado.
     */
    @Transactional
    public void registrarFalta(Long id) {
        Agendamento agendamento = bloquearAgendamento(id);
        exigirAgendado(agendamento, Acao.REGISTRAR_FALTA);

        if (!agendamento.permiteRegistrarFalta(agora()))
            throw new RegraNegocioException("A falta só pode ser registrada depois do horário da consulta.");

        agendamento.marcarFalta();
    }

    private enum Acao { CANCELAR, REAGENDAR, CONFIRMAR_PRESENCA, REGISTRAR_FALTA }

    /** Toda transição parte de AGENDADO. A mensagem explica por que a ação não cabe no status atual. */
    private static void exigirAgendado(Agendamento agendamento, Acao acao) {
        String motivo = switch (agendamento.getStatus()) {
            case AGENDADO -> null;
            case REALIZADO -> switch (acao) {
                case CANCELAR -> "Não é possível cancelar um agendamento já realizado.";
                case REAGENDAR -> "Não é possível reagendar um agendamento já realizado.";
                case CONFIRMAR_PRESENCA, REGISTRAR_FALTA -> PRESENCA_JA_CONFIRMADA;
            };
            case FALTOU -> switch (acao) {
                case CANCELAR -> "Não é possível cancelar um agendamento com falta registrada.";
                case REAGENDAR -> "Não é possível reagendar um agendamento com falta registrada.";
                case CONFIRMAR_PRESENCA, REGISTRAR_FALTA -> FALTA_JA_REGISTRADA;
            };
            case CANCELADO -> switch (acao) {
                case CANCELAR -> "Este agendamento já foi cancelado.";
                case REAGENDAR -> "Não é possível reagendar um agendamento cancelado.";
                case CONFIRMAR_PRESENCA -> "Não é possível confirmar a presença em um agendamento cancelado.";
                case REGISTRAR_FALTA -> "Não é possível registrar falta em um agendamento cancelado.";
            };
        };
        if (motivo != null)
            throw new RegraNegocioException(motivo);
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
