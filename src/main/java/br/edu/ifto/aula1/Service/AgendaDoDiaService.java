package br.edu.ifto.aula1.Service;

import br.edu.ifto.aula1.Model.Entity.Agenda;
import br.edu.ifto.aula1.Model.Entity.Agendamento;
import br.edu.ifto.aula1.Model.Entity.Medico;
import br.edu.ifto.aula1.Model.Entity.StatusAgendamento;
import br.edu.ifto.aula1.Model.repository.AgendaRepository;
import br.edu.ifto.aula1.Model.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agenda do dia da recepção: todos os horários do dia, agrupados por médico, cada um com o
 * agendamento que o ocupa (se houver). São duas consultas ao banco, qualquer que seja o
 * número de horários: os horários com o médico e os agendamentos ativos com o paciente.
 */
@Service
@Transactional(readOnly = true)
public class AgendaDoDiaService {

    private final AgendaRepository agendaRepository;
    private final AgendamentoRepository agendamentoRepository;

    public AgendaDoDiaService(AgendaRepository agendaRepository, AgendamentoRepository agendamentoRepository) {
        this.agendaRepository = agendaRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

    /** Um horário do dia e o agendamento que o ocupa (null se o horário está livre). */
    public record Linha(Agenda agenda, Agendamento agendamento) {
        public boolean isLivre() {
            return agendamento == null;
        }
    }

    public record Grupo(Medico medico, List<Linha> linhas) {
    }

    /** Totais do dia para o resumo no topo da tela. */
    public record Resumo(int horarios, int livres, int aguardando, int realizados, int faltas) {
    }

    public record AgendaDoDia(LocalDate data, List<Grupo> grupos, Resumo resumo) {
        public boolean isVazia() {
            return grupos.isEmpty();
        }
    }

    /** "medicoId" null = todos os médicos. */
    public AgendaDoDia montar(LocalDate data, Long medicoId) {
        List<Agenda> horarios = agendaRepository.buscarDoDia(data, medicoId);
        Map<Long, Agendamento> ocupacao = agendamentoRepository
                .buscarAtivosDoDia(data, medicoId, StatusAgendamento.CANCELADO).stream()
                .collect(Collectors.toMap(ag -> ag.getAgenda().getId(), Function.identity()));

        // Os horários já vêm ordenados por médico e hora; o LinkedHashMap mantém essa ordem.
        Map<Long, Grupo> grupos = new LinkedHashMap<>();
        for (Agenda agenda : horarios) {
            Medico medico = agenda.getMedico();
            grupos.computeIfAbsent(medico.getId(), id -> new Grupo(medico, new ArrayList<>()))
                    .linhas().add(new Linha(agenda, ocupacao.get(agenda.getId())));
        }

        return new AgendaDoDia(data, List.copyOf(grupos.values()), resumir(horarios.size(), ocupacao));
    }

    private static Resumo resumir(int horarios, Map<Long, Agendamento> ocupacao) {
        Map<StatusAgendamento, Long> porStatus = ocupacao.values().stream()
                .collect(Collectors.groupingBy(Agendamento::getStatus, Collectors.counting()));
        return new Resumo(horarios, horarios - ocupacao.size(),
                porStatus.getOrDefault(StatusAgendamento.AGENDADO, 0L).intValue(),
                porStatus.getOrDefault(StatusAgendamento.REALIZADO, 0L).intValue(),
                porStatus.getOrDefault(StatusAgendamento.FALTOU, 0L).intValue());
    }
}
