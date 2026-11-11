package com.pfc.thindesk.service;

import com.pfc.thindesk.dto.AtualizarStatusRequest;
import com.pfc.thindesk.dto.NovoChamadoRequest;
import com.pfc.thindesk.dto.Pagina;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.entity.Tecnico;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import com.pfc.thindesk.exception.RecursoNaoEncontradoException;
import com.pfc.thindesk.exception.RegraNegocioException;
import com.pfc.thindesk.repository.ChamadoRepository;
import com.pfc.thindesk.repository.ClienteRepository;
import com.pfc.thindesk.repository.TecnicoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/** Regras de negocio dos chamados (consulta, abertura e mudanca de status). */
@Service
public class ChamadoService {

    private static final Set<String> CAMPOS_ORDENAVEIS =
            Set.of("dataAbertura", "dataFechamento", "prioridade", "status", "tipo", "protocolo");

    private final ChamadoRepository chamados;
    private final ClienteRepository clientes;
    private final TecnicoRepository tecnicos;
    private final MongoTemplate mongo;
    private final Clock clock;
    private final ZoneId zona;

    public ChamadoService(ChamadoRepository chamados, ClienteRepository clientes, TecnicoRepository tecnicos,
                          MongoTemplate mongo, Clock clock,
                          @Value("${thindesk.fuso-horario:America/Sao_Paulo}") String fuso) {
        this.chamados = chamados;
        this.clientes = clientes;
        this.tecnicos = tecnicos;
        this.mongo = mongo;
        this.clock = clock;
        this.zona = ZoneId.of(fuso);
    }

    /* ------------------------------------------------------------------ */
    /* Consulta paginada                                                   */
    /* ------------------------------------------------------------------ */

    public Pagina<Chamado> listar(List<StatusChamado> status, String tipo, String tecnico, String busca,
                                  Instant de, Instant ate, int pagina, int tamanho, String ordenarPor, String direcao) {
        if (pagina < 0) {
            throw new ParametroInvalidoException("O parâmetro 'pagina' deve ser maior ou igual a 0");
        }
        if (tamanho < 1 || tamanho > 100) {
            throw new ParametroInvalidoException("O parâmetro 'tamanho' deve estar entre 1 e 100");
        }
        String campo = ordenarPor == null || ordenarPor.isBlank() ? "dataAbertura" : ordenarPor;
        if (!CAMPOS_ORDENAVEIS.contains(campo)) {
            throw new ParametroInvalidoException("Não é possível ordenar por '" + campo + "'. Use: " + CAMPOS_ORDENAVEIS);
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(direcao) ? Sort.Direction.ASC : Sort.Direction.DESC;

        List<Criteria> partes = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            partes.add(Criteria.where("status").in(status.stream().map(Enum::name).toList()));
        }
        String t = DashboardService.limpar(tipo);
        if (t != null) {
            partes.add(Criteria.where("tipo").is(t));
        }
        String tec = DashboardService.limpar(tecnico);
        if (tec != null) {
            partes.add(Criteria.where("tecnico").is(tec));
        }
        if (de != null || ate != null) {
            Criteria c = Criteria.where("dataAbertura");
            if (de != null) {
                c = c.gte(Date.from(de));
            }
            if (ate != null) {
                c = c.lt(Date.from(ate));
            }
            partes.add(c);
        }
        String b = DashboardService.limpar(busca);
        if (b != null) {
            Pattern p = Pattern.compile(Pattern.quote(b), Pattern.CASE_INSENSITIVE);
            partes.add(new Criteria().orOperator(
                    Criteria.where("protocolo").regex(p),
                    Criteria.where("titulo").regex(p),
                    Criteria.where("usuario").regex(p)));
        }
        Criteria filtro = partes.isEmpty() ? new Criteria() : new Criteria().andOperator(partes.toArray(new Criteria[0]));

        long total = mongo.count(new Query(filtro), Chamado.class);
        Query consulta = new Query(filtro)
                .with(Sort.by(dir, campo))
                .skip((long) pagina * tamanho)
                .limit(tamanho);
        List<Chamado> conteudo = mongo.find(consulta, Chamado.class);
        return Pagina.of(conteudo, pagina, tamanho, total);
    }

    public Chamado buscar(String id) {
        return chamados.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado não encontrado: " + id));
    }

    /* ------------------------------------------------------------------ */
    /* Abertura                                                            */
    /* ------------------------------------------------------------------ */

    public Chamado criar(NovoChamadoRequest req) {
        Cliente cliente = clientes.findById(req.clienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado: " + req.clienteId()));

        Instant agora = clock.instant();
        Chamado c = new Chamado();
        c.setProtocolo(String.format("TD-%d-%06d", Year.now(clock.withZone(zona)).getValue(), chamados.count() + 1));
        c.setTitulo(req.titulo().trim());
        c.setDescricao(req.descricao() == null ? null : req.descricao().trim());
        c.setTipo(req.tipo().trim());
        c.setPrioridade(req.prioridade());
        c.setCanal(DashboardService.limpar(req.canal()) == null ? "Portal" : req.canal().trim());
        c.setClienteId(cliente.getId());
        c.setUsuario(cliente.getNome());
        c.setSetor(cliente.getSetor());
        c.setStatus(StatusChamado.ABERTO);
        c.setDataAbertura(agora);
        c.setUltimaAtualizacao(agora);
        c.setPrazoSla(agora.plus(Duration.ofHours(req.prioridade().getSlaHoras())));

        if (DashboardService.limpar(req.tecnicoId()) != null) {
            Tecnico tec = tecnicos.findById(req.tecnicoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Técnico não encontrado: " + req.tecnicoId()));
            c.setTecnicoId(tec.getId());
            c.setTecnico(tec.getNome());
        }
        return chamados.save(c);
    }

    /* ------------------------------------------------------------------ */
    /* RF07 - Atualizacao de status (alimenta o dashboard)                 */
    /* ------------------------------------------------------------------ */

    public Chamado atualizarStatus(String id, AtualizarStatusRequest req) {
        Chamado c = buscar(id);
        StatusChamado atual = c.getStatus();
        StatusChamado novo = req.status();

        if (atual == novo) {
            throw new RegraNegocioException("O chamado já está com o status " + novo.getLabel());
        }
        if (atual != null && !atual.podeIrPara(novo)) {
            throw new RegraNegocioException("Transição de status não permitida: " + atual.getLabel()
                    + " → " + novo.getLabel() + ". Permitidas: " + atual.proximosPermitidos());
        }
        String consideracao = DashboardService.limpar(req.consideracaoTecnica());
        if (novo == StatusChamado.RESOLVIDO && consideracao == null && c.getConsideracaoTecnica() == null) {
            throw new RegraNegocioException("Informe a consideração técnica para resolver o chamado");
        }
        if (req.avaliacao() != null && novo != StatusChamado.FECHADO) {
            throw new RegraNegocioException("A avaliação só pode ser registrada ao fechar o chamado");
        }

        Instant agora = clock.instant();
        c.setStatus(novo);
        c.setUltimaAtualizacao(agora);
        if (consideracao != null) {
            c.setConsideracaoTecnica(consideracao);
        }

        switch (novo) {
            case EM_ANDAMENTO -> {
                if (c.getDataPrimeiroAtendimento() == null) {
                    c.setDataPrimeiroAtendimento(agora);
                }
                if (atual == StatusChamado.RESOLVIDO) { // reabertura
                    c.setDataFechamento(null);
                    c.setTempoResolucaoMin(null);
                    c.setSlaCumprido(null);
                }
            }
            case RESOLVIDO -> registrarFechamento(c, agora);
            case FECHADO -> {
                if (c.getDataFechamento() == null) {
                    registrarFechamento(c, agora);
                }
                c.setAvaliacao(req.avaliacao());
            }
            case CANCELADO -> {
                c.setDataFechamento(agora);
                c.setTempoResolucaoMin(null);
                c.setSlaCumprido(null);
            }
            default -> { }
        }
        return chamados.save(c);
    }

    /** Calcula tempo de resolucao e cumprimento de SLA no momento da solucao. */
    static void registrarFechamento(Chamado c, Instant quando) {
        if (c.getDataPrimeiroAtendimento() == null) {
            c.setDataPrimeiroAtendimento(quando);
        }
        c.setDataFechamento(quando);
        if (c.getDataAbertura() != null) {
            c.setTempoResolucaoMin(Math.max(0, Duration.between(c.getDataAbertura(), quando).toMinutes()));
        }
        c.setSlaCumprido(c.getPrazoSla() == null ? null : !quando.isAfter(c.getPrazoSla()));
    }
}
