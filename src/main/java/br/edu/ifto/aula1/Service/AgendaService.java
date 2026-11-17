package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Dto.AgendaFiltro;
import br.edu.ifto.aula1.Dto.AgendaForm;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@Transactional(readOnly = true)
public class AgendaService {

    static final String HORARIO_INDISPONIVEL = "Este horário não está mais disponível.";
    static final String HORARIO_DUPLICADO = "Este médico já tem um horário cadastrado nesta data e hora.";

    private static final Logger log = LoggerFactory.getLogger(AgendaService.class);

    private final AgendaRepository repository;
    private final MedicoService medicoService;

    public AgendaService(AgendaRepository repository, MedicoService medicoService) {
        this.repository = repository;
        this.medicoService = medicoService;
    }

    /** Horários livres que ainda não passaram, filtrados e paginados no banco. */
    public Page<Agenda> listarDisponiveis(AgendaFiltro filtro, Pageable pageable) {
        LocalDateTime agora = LocalDateTime.now();
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

        if (!agenda.isDisponivel() || !agenda.getDataHora().isAfter(LocalDateTime.now()))
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
        if (form.getMedicoId() == null || !medicoService.existe(form.getMedicoId()))
            throw new RegraNegocioException("medicoId", "Médico não encontrado.");
        if (form.getData() == null)
            throw new RegraNegocioException("data", "Informe a data.");
        if (form.getHorario() == null)
            throw new RegraNegocioException("horario", "Informe o horário.");

        LocalDate data = form.getData();
        // Segundos não fazem parte de um horário de agenda; sem eles, 08:00 e 08:00:30 são o mesmo horário.
        LocalTime horario = form.getHorario().withSecond(0).withNano(0);

        if (data.isBefore(LocalDate.now()))
            throw new RegraNegocioException("data", "A data não pode estar no passado.");
        if (!LocalDateTime.of(data, horario).isAfter(LocalDateTime.now()))
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
}
