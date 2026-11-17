package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Dto.AgendaFiltro;
import br.edu.ifto.aula1.Dto.AgendaForm;
import br.edu.ifto.aula1.Dto.AgendaLoteForm;
import br.edu.ifto.aula1.Model.Entity.Agenda;
import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.Entity.StatusAgenda;
import br.edu.ifto.aula1.Model.repository.AgendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AgendaService {

    static final String HORARIO_INDISPONIVEL = "Este horário não está mais disponível.";
    static final String HORARIO_DUPLICADO = "Este médico já tem um horário cadastrado nesta data e hora.";

    /** Limites da geração em lote, para que um erro de digitação não crie milhares de horários. */
    static final int PERIODO_MAXIMO_DIAS = 92;
    static final int MAXIMO_HORARIOS_LOTE = 1000;

    private static final Logger log = LoggerFactory.getLogger(AgendaService.class);

    private final AgendaRepository repository;
    private final MedicoService medicoService;
    private final Clock clock;

    public AgendaService(AgendaRepository repository, MedicoService medicoService, Clock clock) {
        this.repository = repository;
        this.medicoService = medicoService;
        this.clock = clock;
    }

    /** Horários livres que ainda não passaram, filtrados e paginados no banco. */
    public Page<Agenda> listarDisponiveis(AgendaFiltro filtro, Pageable pageable) {
        LocalDateTime agora = LocalDateTime.now(clock);
        return repository.buscarFuturos(StatusAgenda.DISPONIVEL, agora.toLocalDate(), agora.toLocalTime(),
                filtro.getMedicoId(), filtro.getData(), filtro.getEspecialidade(), pageable);
    }

    /**
     * Horário para a tela de confirmação do agendamento, já com o médico carregado.
     * A disponibilidade conferida aqui serve só à tela: ela é verificada de novo, com
     * bloqueio, em AgendamentoService.agendar.
     */
    public Agenda buscarDisponivel(Long id) {
        if (id == null)
            throw new RegistroNaoEncontradoException("Horário não encontrado.");

        Agenda agenda = repository.findComMedicoById(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Horário não encontrado."));

        if (!agenda.isDisponivel() || !agenda.getDataHora().isAfter(LocalDateTime.now(clock)))
            throw new RegraNegocioException(HORARIO_INDISPONIVEL);

        return agenda;
    }

    /**
     * Disponibiliza um horário do médico, sempre com o status DISPONIVEL. As regras são
     * conferidas aqui mesmo que o formulário já as valide, porque o serviço não confia
     * em quem o chama.
     */
    @Transactional
    public Agenda cadastrar(AgendaForm form) {
        exigirMedico(form.getMedicoId());
        if (form.getData() == null)
            throw new RegraNegocioException("data", "Informe a data.");
        if (form.getHorario() == null)
            throw new RegraNegocioException("horario", "Informe o horário.");

        LocalDate data = form.getData();
        // Segundos não fazem parte de um horário de agenda; sem eles, 08:00 e 08:00:30 são o mesmo horário.
        LocalTime horario = form.getHorario().withSecond(0).withNano(0);
        LocalDateTime agora = LocalDateTime.now(clock);

        if (data.isBefore(agora.toLocalDate()))
            throw new RegraNegocioException("data", "A data não pode estar no passado.");
        if (!LocalDateTime.of(data, horario).isAfter(agora))
            throw new RegraNegocioException("horario", "O horário já passou.");

        Medico medico = medicoService.buscar(form.getMedicoId());
        if (repository.existsByMedicoIdAndDataAndHorario(medico.getId(), data, horario))
            throw new RegraNegocioException("horario", HORARIO_DUPLICADO);

        try {
            // O flush antecipa o INSERT para que a restrição única do banco seja conferida aqui.
            return repository.saveAndFlush(new Agenda(medico, data, horario));
        } catch (DataIntegrityViolationException e) {
            // Dois cadastros simultâneos do mesmo horário: o segundo é barrado pela restrição única.
            log.warn("Cadastro simultâneo do mesmo horário barrado pelo banco: médico {}, {} {}",
                    medico.getId(), data, horario);
            throw new RegraNegocioException("horario", HORARIO_DUPLICADO);
        }
    }

    /** Quantos horários a geração em lote criou e quantos pulou (já existiam ou já passaram). */
    public record ResultadoLote(int gerados, int ignorados) {
    }

    /**
     * Gera os horários do médico no período: em cada dia da semana escolhido, um horário a
     * cada "duracaoMinutos", da hora inicial até a hora final (a última consulta termina, no
     * máximo, na hora final). Horários que já existem ou que já passaram são pulados, então
     * o mesmo lote pode ser gerado de novo sem erro. Tudo é gravado na mesma transação.
     */
    @Transactional
    public ResultadoLote gerarEmLote(AgendaLoteForm form) {
        exigirMedico(form.getMedicoId());
        validarLote(form);

        Medico medico = medicoService.buscar(form.getMedicoId());
        LocalDateTime agora = LocalDateTime.now(clock);

        Set<LocalDateTime> existentes = repository
                .findByMedicoIdAndDataBetween(medico.getId(), form.getDataInicio(), form.getDataFim()).stream()
                .map(Agenda::getDataHora)
                .collect(Collectors.toSet());

        List<Agenda> novos = new ArrayList<>();
        int ignorados = 0;
        for (LocalDateTime dataHora : horariosDoLote(form)) {
            if (!dataHora.isAfter(agora) || existentes.contains(dataHora))
                ignorados++;
            else
                novos.add(new Agenda(medico, dataHora.toLocalDate(), dataHora.toLocalTime()));
        }

        if (novos.isEmpty())
            throw new RegraNegocioException(
                    "Nenhum horário novo para gerar: os horários do período já estavam cadastrados ou já passaram.");
        if (novos.size() > MAXIMO_HORARIOS_LOTE)
            throw new RegraNegocioException("O lote geraria " + novos.size() + " horários; o máximo é "
                    + MAXIMO_HORARIOS_LOTE + ". Reduza o período ou aumente a duração das consultas.");

        try {
            repository.saveAllAndFlush(novos);
        } catch (DataIntegrityViolationException e) {
            // Alguém cadastrou um dos horários entre a leitura acima e a gravação: nada é gravado.
            log.warn("Geração em lote do médico {} barrada pela restrição única", medico.getId());
            throw new RegraNegocioException(
                    "A agenda deste médico foi alterada por outra pessoa ao mesmo tempo. Tente novamente.");
        }

        log.info("Lote gerado para o médico {}: {} horários criados, {} ignorados",
                medico.getId(), novos.size(), ignorados);
        return new ResultadoLote(novos.size(), ignorados);
    }

    private void validarLote(AgendaLoteForm form) {
        LocalDate inicio = form.getDataInicio();
        LocalDate fim = form.getDataFim();

        if (inicio == null)
            throw new RegraNegocioException("dataInicio", "Informe a data inicial.");
        if (fim == null)
            throw new RegraNegocioException("dataFim", "Informe a data final.");
        if (inicio.isBefore(LocalDate.now(clock)))
            throw new RegraNegocioException("dataInicio", "A data inicial não pode estar no passado.");
        if (fim.isBefore(inicio))
            throw new RegraNegocioException("dataFim", "A data final deve ser igual ou posterior à inicial.");
        if (ChronoUnit.DAYS.between(inicio, fim) >= PERIODO_MAXIMO_DIAS)
            throw new RegraNegocioException("dataFim", "O período pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.");

        if (form.getDiasSemana() == null || form.getDiasSemana().isEmpty())
            throw new RegraNegocioException("diasSemana", "Selecione ao menos um dia da semana.");

        Integer duracao = form.getDuracaoMinutos();
        if (duracao == null || duracao < 10 || duracao > 240)
            throw new RegraNegocioException("duracaoMinutos", "A duração deve ficar entre 10 e 240 minutos.");

        if (form.getHoraInicio() == null)
            throw new RegraNegocioException("horaInicio", "Informe o horário inicial.");
        if (form.getHoraFim() == null)
            throw new RegraNegocioException("horaFim", "Informe o horário final.");
        if (!form.getHoraFim().isAfter(form.getHoraInicio()))
            throw new RegraNegocioException("horaFim", "O horário final deve ser depois do inicial.");
        if (form.getHoraInicio().plusMinutes(duracao).isAfter(form.getHoraFim()))
            throw new RegraNegocioException("horaFim", "O intervalo é menor que a duração de uma consulta.");
    }

    /** Todos os horários do lote, em ordem. Trabalha em minutos do dia para não "virar" a meia-noite. */
    private static List<LocalDateTime> horariosDoLote(AgendaLoteForm form) {
        int inicio = form.getHoraInicio().getHour() * 60 + form.getHoraInicio().getMinute();
        int fim = form.getHoraFim().getHour() * 60 + form.getHoraFim().getMinute();
        int duracao = form.getDuracaoMinutos();

        List<LocalDateTime> horarios = new ArrayList<>();
        for (LocalDate data = form.getDataInicio(); !data.isAfter(form.getDataFim()); data = data.plusDays(1)) {
            if (!form.getDiasSemana().contains(data.getDayOfWeek()))
                continue;
            for (int minuto = inicio; minuto + duracao <= fim; minuto += duracao)
                horarios.add(LocalDateTime.of(data, LocalTime.of(minuto / 60, minuto % 60)));
        }
        return horarios;
    }

    private void exigirMedico(Long medicoId) {
        if (medicoId == null || !medicoService.existe(medicoId))
            throw new RegraNegocioException("medicoId", "Médico não encontrado.");
    }
}
