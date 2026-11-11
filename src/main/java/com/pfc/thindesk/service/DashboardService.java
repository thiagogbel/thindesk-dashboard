package com.pfc.thindesk.service;

import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.Granularidade;
import com.pfc.thindesk.dto.ItemDistribuicao;
import com.pfc.thindesk.dto.OpcaoFiltro;
import com.pfc.thindesk.dto.OpcoesFiltro;
import com.pfc.thindesk.dto.PontoSerie;
import com.pfc.thindesk.dto.RankingTecnico;
import com.pfc.thindesk.dto.RankingTipo;
import com.pfc.thindesk.dto.ResumoDashboard;
import com.pfc.thindesk.dto.SerieTemporal;
import com.pfc.thindesk.entity.Prioridade;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import com.pfc.thindesk.repository.DashboardConsultas;
import com.pfc.thindesk.repository.DashboardConsultas.Estatistica;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Regras de negocio do Dashboard Administrativo.
 * Recebe os parametros "crus" da API, valida/normaliza (RF05, RF07),
 * delega as agregacoes ao banco e trata o resultado (percentuais,
 * conversao de minutos para horas, preenchimento de periodos sem dados).
 */
@Service
public class DashboardService {

    static final int PERIODO_PADRAO_DIAS = 90;
    static final int PERIODO_MAXIMO_DIAS = 731;
    static final int MAXIMO_PONTOS_DIA = 366;
    static final String NAO_ATRIBUIDO = "Não atribuído";
    static final String NAO_INFORMADO = "Não informado";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final DashboardConsultas consultas;
    private final Clock clock;
    private final ZoneId zona;

    public DashboardService(DashboardConsultas consultas, Clock clock,
                            @Value("${thindesk.fuso-horario:America/Sao_Paulo}") String fuso) {
        this.consultas = consultas;
        this.clock = clock;
        this.zona = ZoneId.of(fuso);
    }

    /* ================================================================== */
    /* RF05 - Filtros                                                      */
    /* ================================================================== */

    public FiltroDashboard montarFiltro(LocalDate inicio, LocalDate fim, List<StatusChamado> status,
                                        String tipo, Prioridade prioridade, String tecnico, String setor) {
        LocalDate hoje = LocalDate.now(clock.withZone(zona));
        if (fim == null) {
            fim = inicio == null ? hoje : hoje.isBefore(inicio) ? inicio : hoje;
        }
        if (inicio == null) {
            inicio = fim.minusDays(PERIODO_PADRAO_DIAS - 1L);
        }
        if (inicio.isAfter(fim)) {
            throw new ParametroInvalidoException("A data inicial (" + inicio + ") não pode ser maior que a data final (" + fim + ")");
        }
        long dias = java.time.temporal.ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > PERIODO_MAXIMO_DIAS) {
            throw new ParametroInvalidoException("O período máximo de consulta é de " + PERIODO_MAXIMO_DIAS + " dias");
        }
        Instant inicioUtc = inicio.atStartOfDay(zona).toInstant();
        Instant fimUtc = fim.plusDays(1).atStartOfDay(zona).toInstant();
        return new FiltroDashboard(inicio, fim, inicioUtc, fimUtc, status == null ? List.of() : status.stream().distinct().toList(),
                limpar(tipo), prioridade, limpar(tecnico), limpar(setor));
    }

    /** Remove espacos e trata vazio/"todos" como "sem filtro". */
    static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim().replaceAll("\\s+", " ");
        return v.isEmpty() || v.equalsIgnoreCase("todos") ? null : v;
    }

    /* ================================================================== */
    /* RF01 - Indicadores gerais                                           */
    /* ================================================================== */

    public ResumoDashboard resumo(FiltroDashboard f) {
        Map<String, Long> porStatus = consultas.contarPor("status", f, DashboardConsultas.DATA_ABERTURA, false);
        long abertos = porStatus.getOrDefault(StatusChamado.ABERTO.name(), 0L);
        long andamento = porStatus.getOrDefault(StatusChamado.EM_ANDAMENTO.name(), 0L);
        long aguardando = porStatus.getOrDefault(StatusChamado.AGUARDANDO_CLIENTE.name(), 0L);
        long resolvidos = porStatus.getOrDefault(StatusChamado.RESOLVIDO.name(), 0L);
        long fechados = porStatus.getOrDefault(StatusChamado.FECHADO.name(), 0L);
        long cancelados = porStatus.getOrDefault(StatusChamado.CANCELADO.name(), 0L);
        long total = porStatus.values().stream().mapToLong(Long::longValue).sum();

        Estatistica atendidos = consultas.estatisticasGerais(f, DashboardConsultas.DATA_FECHAMENTO, true);

        long anterior = consultas.contar(periodoAnterior(f), DashboardConsultas.DATA_ABERTURA, false);
        Double variacao = anterior == 0 ? null : arred((total - anterior) * 100.0 / anterior);

        long atrasados = consultas.contarAtrasados(f, clock.instant());

        return new ResumoDashboard(
                f.inicio(), f.fim(), total, anterior, variacao,
                abertos, andamento, aguardando, resolvidos, fechados, cancelados,
                abertos + andamento + aguardando,
                atrasados,
                atendidos.total(),
                taxaSla(atendidos),
                horas(atendidos.tempoMedioMin()),
                arred(atendidos.avaliacaoMedia()),
                clock.instant());
    }

    /** Mesmo numero de dias, imediatamente antes do periodo atual. */
    FiltroDashboard periodoAnterior(FiltroDashboard f) {
        LocalDate fimAnterior = f.inicio().minusDays(1);
        LocalDate inicioAnterior = fimAnterior.minusDays(f.dias() - 1);
        return new FiltroDashboard(inicioAnterior, fimAnterior,
                inicioAnterior.atStartOfDay(zona).toInstant(), f.inicioUtc(),
                f.status(), f.tipo(), f.prioridade(), f.tecnico(), f.setor());
    }

    /* ================================================================== */
    /* RF02 - Chamados abertos x atendidos por periodo                     */
    /* ================================================================== */

    public Granularidade resolverGranularidade(Granularidade pedida, FiltroDashboard f) {
        Granularidade g = pedida == null ? Granularidade.AUTO : pedida;
        if (g == Granularidade.AUTO) {
            long dias = f.dias();
            return dias <= 45 ? Granularidade.DIA : dias <= 180 ? Granularidade.SEMANA : Granularidade.MES;
        }
        if (g == Granularidade.DIA && f.dias() > MAXIMO_PONTOS_DIA) {
            throw new ParametroInvalidoException("Granularidade DIA permite no máximo " + MAXIMO_PONTOS_DIA
                    + " dias; use SEMANA ou MES");
        }
        return g;
    }

    public SerieTemporal seriePorPeriodo(FiltroDashboard f, Granularidade pedida) {
        Granularidade g = resolverGranularidade(pedida, f);
        String formato = switch (g) {
            case DIA -> "%Y-%m-%d";
            case SEMANA -> "%G-W%V";
            default -> "%Y-%m";
        };
        String fuso = zona.getId();
        Map<String, Long> abertos = consultas.contarPorPeriodo(formato, f, DashboardConsultas.DATA_ABERTURA, false, fuso);
        Map<String, Long> atendidos = consultas.contarPorPeriodo(formato, f, DashboardConsultas.DATA_FECHAMENTO, true, fuso);

        List<PontoSerie> pontos = new ArrayList<>();
        long totalAbertos = 0;
        long totalAtendidos = 0;
        for (String[] periodo : periodos(g, f.inicio(), f.fim())) {
            long a = abertos.getOrDefault(periodo[0], 0L);
            long t = atendidos.getOrDefault(periodo[0], 0L);
            totalAbertos += a;
            totalAtendidos += t;
            pontos.add(new PontoSerie(periodo[0], periodo[1], a, t));
        }
        return new SerieTemporal(g, f.inicio(), f.fim(), totalAbertos, totalAtendidos, pontos);
    }

    /**
     * Gera todas as chaves do periodo (chave, rotulo), inclusive as que nao
     * tem chamados, para o grafico nao "pular" dias/semanas/meses vazios.
     */
    static List<String[]> periodos(Granularidade g, LocalDate inicio, LocalDate fim) {
        List<String[]> lista = new ArrayList<>();
        switch (g) {
            case DIA -> {
                for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
                    lista.add(new String[] {d.toString(), d.format(DIA_MES)});
                }
            }
            case SEMANA -> {
                for (LocalDate d = inicio.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                     !d.isAfter(fim); d = d.plusWeeks(1)) {
                    String chave = String.format("%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR),
                            d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                    LocalDate exibido = d.isBefore(inicio) ? inicio : d;
                    lista.add(new String[] {chave, "Sem " + exibido.format(DIA_MES)});
                }
            }
            default -> {
                for (YearMonth m = YearMonth.from(inicio); !m.isAfter(YearMonth.from(fim)); m = m.plusMonths(1)) {
                    String mes = m.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
                    lista.add(new String[] {m.toString(), mes + "/" + String.valueOf(m.getYear()).substring(2)});
                }
            }
        }
        return lista;
    }

    /* ================================================================== */
    /* RF03 - Situacao dos chamados                                        */
    /* ================================================================== */

    public List<ItemDistribuicao> distribuicaoStatus(FiltroDashboard f) {
        Map<String, Long> porStatus = consultas.contarPor("status", f, DashboardConsultas.DATA_ABERTURA, false);
        long total = porStatus.values().stream().mapToLong(Long::longValue).sum();
        List<StatusChamado> exibidos = f.status().isEmpty() ? Arrays.asList(StatusChamado.values()) : f.status();
        return exibidos.stream()
                .map(s -> {
                    long n = porStatus.getOrDefault(s.name(), 0L);
                    return new ItemDistribuicao(s.name(), s.getLabel(), n, percentual(n, total));
                })
                .toList();
    }

    /** Distribuicao por outras dimensoes: prioridade, setor, canal ou tipo. */
    public List<ItemDistribuicao> distribuicao(String dimensao, FiltroDashboard f) {
        String campo = dimensao == null ? "" : dimensao.trim().toLowerCase(Locale.ROOT);
        if (!List.of("prioridade", "setor", "canal", "tipo").contains(campo)) {
            throw new ParametroInvalidoException("Dimensão inválida: '" + dimensao
                    + "'. Use prioridade, setor, canal ou tipo");
        }
        Map<String, Long> contagem = consultas.contarPor(campo, f, DashboardConsultas.DATA_ABERTURA, false);
        long total = contagem.values().stream().mapToLong(Long::longValue).sum();
        if (campo.equals("prioridade")) {
            return Arrays.stream(Prioridade.values())
                    .map(p -> {
                        long n = contagem.getOrDefault(p.name(), 0L);
                        return new ItemDistribuicao(p.name(), p.getLabel(), n, percentual(n, total));
                    })
                    .toList();
        }
        return contagem.entrySet().stream()
                .map(e -> {
                    String chave = e.getKey() == null || e.getKey().isBlank() ? NAO_INFORMADO : e.getKey();
                    return new ItemDistribuicao(chave, chave, e.getValue(), percentual(e.getValue(), total));
                })
                .toList();
    }

    /* ================================================================== */
    /* RF04 - Tipos de chamados mais atendidos / ranking de tecnicos       */
    /* ================================================================== */

    public List<RankingTipo> rankingTipos(FiltroDashboard f, int limite) {
        validarLimite(limite);
        long total = consultas.contar(f, DashboardConsultas.DATA_ABERTURA, false);
        List<Estatistica> stats = consultas.estatisticasPor("tipo", f, DashboardConsultas.DATA_ABERTURA, false, limite);
        List<RankingTipo> ranking = new ArrayList<>();
        int pos = 1;
        for (Estatistica e : stats) {
            ranking.add(new RankingTipo(pos++, e.chave() == null ? NAO_INFORMADO : e.chave(), e.total(), e.atendidos(),
                    percentual(e.total(), total), horas(e.tempoMedioMin()), taxaSla(e)));
        }
        return ranking;
    }

    public List<RankingTecnico> rankingTecnicos(FiltroDashboard f, int limite) {
        validarLimite(limite);
        List<Estatistica> stats = consultas.estatisticasPor("tecnico", f, DashboardConsultas.DATA_FECHAMENTO, true, limite);
        List<RankingTecnico> ranking = new ArrayList<>();
        int pos = 1;
        for (Estatistica e : stats) {
            ranking.add(new RankingTecnico(pos++, e.chave() == null ? NAO_ATRIBUIDO : e.chave(), e.total(),
                    horas(e.tempoMedioMin()), taxaSla(e), arred(e.avaliacaoMedia())));
        }
        return ranking;
    }

    /* ================================================================== */
    /* Opcoes de filtro                                                    */
    /* ================================================================== */

    public OpcoesFiltro opcoesFiltro() {
        String fuso = zona.getId();
        return new OpcoesFiltro(
                Arrays.stream(StatusChamado.values()).map(s -> new OpcaoFiltro(s.name(), s.getLabel())).toList(),
                Arrays.stream(Prioridade.values()).map(p -> new OpcaoFiltro(p.name(), p.getLabel())).toList(),
                consultas.valoresDistintos("tipo"),
                consultas.valoresDistintos("tecnico"),
                consultas.valoresDistintos("setor"),
                consultas.dataMinima(fuso),
                consultas.dataMaxima(fuso));
    }

    /* ================================================================== */
    /* Utilitarios de tratamento                                           */
    /* ================================================================== */

    static void validarLimite(int limite) {
        if (limite < 1 || limite > 50) {
            throw new ParametroInvalidoException("O parâmetro 'limite' deve estar entre 1 e 50");
        }
    }

    static double percentual(long parte, long total) {
        return total == 0 ? 0.0 : arred(parte * 100.0 / total);
    }

    static Double taxaSla(Estatistica e) {
        long base = e.dentroSla() + e.foraSla();
        return base == 0 ? null : arred(e.dentroSla() * 100.0 / base);
    }

    static Double horas(Double minutos) {
        return minutos == null ? null : arred(minutos / 60.0);
    }

    static Double arred(Double v) {
        return v == null ? null : Math.round(v * 10.0) / 10.0;
    }
}
