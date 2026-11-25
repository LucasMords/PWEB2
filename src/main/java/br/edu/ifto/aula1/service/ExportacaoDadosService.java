package br.edu.ifto.aula1.service;

import br.edu.ifto.aula1.model.entity.AcaoAuditoria;
import br.edu.ifto.aula1.model.entity.Agendamento;
import br.edu.ifto.aula1.model.entity.Consulta;
import br.edu.ifto.aula1.model.entity.Paciente;
import br.edu.ifto.aula1.model.repository.AgendamentoRepository;
import br.edu.ifto.aula1.model.repository.ConsultaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * LGPD, direito de acesso (art. 18, II): o paciente baixa, num arquivo JSON, tudo o que a
 * clínica guarda sobre ele: dados cadastrais, agendamentos (inclusive cancelados) e
 * consultas. Só os dados do próprio titular; cada exportação fica na auditoria.
 */
@Service
@Transactional(readOnly = true)
public class ExportacaoDadosService {

    private final AgendamentoRepository agendamentoRepository;
    private final ConsultaRepository consultaRepository;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ExportacaoDadosService(AgendamentoRepository agendamentoRepository, ConsultaRepository consultaRepository,
                                  AuditoriaService auditoria, Clock clock) {
        this.agendamentoRepository = agendamentoRepository;
        this.consultaRepository = consultaRepository;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    public record DadosPessoais(String nome, String cpf, LocalDate dataNascimento, String telefone,
                                String email, String login, LocalDateTime consentimentoEm) {
    }

    public record AgendamentoExportado(LocalDate data, LocalTime horario, String medico, String especialidade,
                                       String situacao, LocalDateTime agendadoEm, LocalDateTime canceladoEm,
                                       String motivoCancelamento) {
    }

    public record ConsultaExportada(LocalDateTime realizadaEm, String medico, String especialidade,
                                    String observacoes) {
    }

    public record Exportacao(LocalDateTime geradoEm, DadosPessoais paciente,
                             List<AgendamentoExportado> agendamentos, List<ConsultaExportada> consultas) {
    }

    /** "login" é o da conta autenticada; o paciente vem dela, nunca de um id da requisição. */
    @Transactional
    public Exportacao exportar(Paciente paciente, String login) {
        DadosPessoais dados = new DadosPessoais(paciente.getNome(), paciente.getCpf(), paciente.getDataNascimento(),
                paciente.getTelefone(), paciente.getEmail(), login, paciente.getConsentimentoEm());

        List<AgendamentoExportado> agendamentos = agendamentoRepository
                .buscar(paciente.getId(), null, null, null, Pageable.unpaged()).stream()
                .map(ExportacaoDadosService::exportar)
                .toList();

        List<ConsultaExportada> consultas = consultaRepository
                .findByAgendamentoPacienteIdOrderByDataRealizacaoDesc(paciente.getId()).stream()
                .map(ExportacaoDadosService::exportar)
                .toList();

        auditoria.registrar(AcaoAuditoria.DADOS_EXPORTADOS, "Paciente", paciente.getId(),
                agendamentos.size() + " agendamentos, " + consultas.size() + " consultas");
        return new Exportacao(LocalDateTime.now(clock).withNano(0), dados, agendamentos, consultas);
    }

    private static AgendamentoExportado exportar(Agendamento agendamento) {
        var agenda = agendamento.getAgenda();
        return new AgendamentoExportado(agenda.getData(), agenda.getHorario(), agenda.getMedico().getNome(),
                agenda.getMedico().getEspecialidade(), agendamento.getStatus().getDescricao(),
                agendamento.getDataCriacao(), agendamento.getDataCancelamento(), agendamento.getMotivoCancelamento());
    }

    private static ConsultaExportada exportar(Consulta consulta) {
        var medico = consulta.getMedico();
        return new ConsultaExportada(consulta.getDataRealizacao(), medico.getNome(), medico.getEspecialidade(),
                consulta.getObservacoes());
    }
}
