package com.pfc.thindesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.Granularidade;
import com.pfc.thindesk.dto.ItemDistribuicao;
import com.pfc.thindesk.dto.PontoSerie;
import com.pfc.thindesk.dto.RankingTecnico;
import com.pfc.thindesk.dto.RankingTipo;
import com.pfc.thindesk.dto.ResumoDashboard;
import com.pfc.thindesk.dto.SerieTemporal;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import com.pfc.thindesk.repository.DashboardConsultas;
import com.pfc.thindesk.repository.DashboardConsultas.Estatistica;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    /** "Agora" fixo: 11/11/2026 14:00 em Sao Paulo. */
    private static final Instant AGORA = Instant.parse("2026-11-11T17:00:00Z");

    private DashboardConsultas consultas;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        consultas = mock(DashboardConsultas.class);
        service = new DashboardService(consultas, Clock.fixed(AGORA, ZoneOffset.UTC), "America/Sao_Paulo");
    }

    private FiltroDashboard filtro(String inicio, String fim) {
        return service.montarFiltro(LocalDate.parse(inicio), LocalDate.parse(fim), null, null, null, null, null);
    }

    /* ---------------- RF05 - filtros ---------------- */

    @Test
    @DisplayName("Sem datas, o periodo padrao e de 90 dias terminando hoje")
    void periodoPadrao() {
        FiltroDashboard f = service.montarFiltro(null, null, null, null, null, null, null);
        assertThat(f.fim()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(f.inicio()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(f.dias()).isEqualTo(90);
        // meia-noite em Sao Paulo (UTC-3) = 03:00 UTC; fim exclusivo no dia seguinte
        assertThat(f.inicioUtc()).isEqualTo(Instant.parse("2026-08-14T03:00:00Z"));
        assertThat(f.fimUtc()).isEqualTo(Instant.parse("2026-11-12T03:00:00Z"));
    }

    @Test
    @DisplayName("Data inicial maior que a final gera erro 400")
    void inicioMaiorQueFim() {
        assertThatThrownBy(() -> filtro("2026-05-01", "2026-01-01"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("não pode ser maior");
    }

    @Test
    @DisplayName("Periodo acima de 731 dias e recusado")
    void periodoMuitoLongo() {
        assertThatThrownBy(() -> filtro("2022-01-01", "2026-01-01"))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("731");
    }

    @Test
    @DisplayName("Textos de filtro sao normalizados (espacos, vazio e 'todos')")
    void normalizacaoDeTexto() {
        FiltroDashboard f = service.montarFiltro(null, null, List.of(StatusChamado.ABERTO, StatusChamado.ABERTO),
                "  Hardware  ", null, "todos", "   ");
        assertThat(f.tipo()).isEqualTo("Hardware");
        assertThat(f.tecnico()).isNull();
        assertThat(f.setor()).isNull();
        assertThat(f.status()).containsExactly(StatusChamado.ABERTO);
    }

    /* ---------------- RF01 - indicadores ---------------- */

    @Test
    @DisplayName("Resumo soma os status, calcula backlog, SLA, horas e variacao")
    void resumo() {
        Map<String, Long> porStatus = new LinkedHashMap<>();
        porStatus.put("ABERTO", 10L);
        porStatus.put("EM_ANDAMENTO", 20L);
        porStatus.put("AGUARDANDO_CLIENTE", 5L);
        porStatus.put("RESOLVIDO", 15L);
        porStatus.put("FECHADO", 45L);
        porStatus.put("CANCELADO", 5L);
        when(consultas.contarPor(eq("status"), any(), eq(DashboardConsultas.DATA_ABERTURA), eq(false))).thenReturn(porStatus);
        when(consultas.estatisticasGerais(any(), eq(DashboardConsultas.DATA_FECHAMENTO), eq(true)))
                .thenReturn(new Estatistica(null, 60, 60, 150.0, 4.25, 45, 15));
        when(consultas.contar(any(), eq(DashboardConsultas.DATA_ABERTURA), eq(false))).thenReturn(80L);
        when(consultas.contarAtrasados(any(), eq(AGORA))).thenReturn(7L);

        ResumoDashboard r = service.resumo(filtro("2026-10-01", "2026-10-31"));

        assertThat(r.totalAbertosNoPeriodo()).isEqualTo(100);
        assertThat(r.backlog()).isEqualTo(35);
        assertThat(r.atendidosNoPeriodo()).isEqualTo(60);
        assertThat(r.taxaSla()).isEqualTo(75.0);                 // 45 / (45 + 15)
        assertThat(r.tempoMedioResolucaoHoras()).isEqualTo(2.5); // 150 min
        assertThat(r.avaliacaoMedia()).isEqualTo(4.3);           // arredondado
        assertThat(r.variacaoPercentual()).isEqualTo(25.0);      // 100 vs 80
        assertThat(r.atrasados()).isEqualTo(7);
    }

    @Test
    @DisplayName("Sem dados: indicadores zerados e taxas nulas (sem divisao por zero)")
    void resumoVazio() {
        when(consultas.contarPor(any(), any(), any(), eq(false))).thenReturn(Map.of());
        when(consultas.estatisticasGerais(any(), any(), eq(true))).thenReturn(new Estatistica(null, 0, 0, null, null, 0, 0));
        when(consultas.contar(any(), any(), eq(false))).thenReturn(0L);

        ResumoDashboard r = service.resumo(filtro("2026-10-01", "2026-10-31"));

        assertThat(r.totalAbertosNoPeriodo()).isZero();
        assertThat(r.taxaSla()).isNull();
        assertThat(r.tempoMedioResolucaoHoras()).isNull();
        assertThat(r.variacaoPercentual()).isNull();
    }

    @Test
    @DisplayName("Periodo anterior tem o mesmo tamanho e termina na vespera do inicio")
    void periodoAnterior() {
        FiltroDashboard anterior = service.periodoAnterior(filtro("2026-10-01", "2026-10-31"));
        assertThat(anterior.inicio()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(anterior.fim()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    /* ---------------- RF02 - serie temporal ---------------- */

    @Test
    @DisplayName("Granularidade automatica: dia ate 45 dias, semana ate 180, depois mes")
    void granularidadeAutomatica() {
        assertThat(service.resolverGranularidade(Granularidade.AUTO, filtro("2026-10-01", "2026-10-31"))).isEqualTo(Granularidade.DIA);
        assertThat(service.resolverGranularidade(null, filtro("2026-06-01", "2026-10-31"))).isEqualTo(Granularidade.SEMANA);
        assertThat(service.resolverGranularidade(Granularidade.AUTO, filtro("2025-06-01", "2026-10-31"))).isEqualTo(Granularidade.MES);
    }

    @Test
    @DisplayName("DIA com mais de 366 dias e recusado")
    void granularidadeDiaLonga() {
        assertThatThrownBy(() -> service.resolverGranularidade(Granularidade.DIA, filtro("2025-01-01", "2026-10-31")))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    @DisplayName("Serie preenche com zero os periodos sem chamados")
    void seriePreencheLacunas() {
        when(consultas.contarPorPeriodo(eq("%Y-%m"), any(), eq(DashboardConsultas.DATA_ABERTURA), eq(false), any()))
                .thenReturn(Map.of("2026-01", 30L, "2026-03", 10L));
        when(consultas.contarPorPeriodo(eq("%Y-%m"), any(), eq(DashboardConsultas.DATA_FECHAMENTO), eq(true), any()))
                .thenReturn(Map.of("2026-02", 25L));

        SerieTemporal s = service.seriePorPeriodo(filtro("2026-01-01", "2026-03-31"), Granularidade.MES);

        assertThat(s.pontos()).extracting(PontoSerie::periodo).containsExactly("2026-01", "2026-02", "2026-03");
        assertThat(s.pontos()).extracting(PontoSerie::abertos).containsExactly(30L, 0L, 10L);
        assertThat(s.pontos()).extracting(PontoSerie::atendidos).containsExactly(0L, 25L, 0L);
        assertThat(s.pontos().get(0).rotulo()).isEqualTo("jan/26");
        assertThat(s.totalAbertos()).isEqualTo(40);
        assertThat(s.totalAtendidos()).isEqualTo(25);
    }

    @Test
    @DisplayName("Chaves semanais seguem a ISO (compativel com %G-W%V do MongoDB), inclusive na virada do ano")
    void chavesSemanais() {
        List<String[]> semanas = DashboardService.periodos(Granularidade.SEMANA, LocalDate.of(2025, 12, 24), LocalDate.of(2026, 1, 10));
        assertThat(semanas).extracting(p -> p[0]).containsExactly("2025-W52", "2026-W01", "2026-W02");
    }

    /* ---------------- RF03 / RF04 ---------------- */

    @Test
    @DisplayName("Distribuicao de status inclui todos os status, mesmo com zero, e calcula percentual")
    void distribuicaoStatus() {
        when(consultas.contarPor(eq("status"), any(), any(), eq(false))).thenReturn(Map.of("ABERTO", 25L, "FECHADO", 75L));

        List<ItemDistribuicao> lista = service.distribuicaoStatus(filtro("2026-10-01", "2026-10-31"));

        assertThat(lista).hasSize(StatusChamado.values().length);
        assertThat(lista.get(0).rotulo()).isEqualTo("Aberto");
        assertThat(lista.get(0).percentual()).isEqualTo(25.0);
        assertThat(lista).filteredOn(i -> i.chave().equals("CANCELADO")).first().extracting(ItemDistribuicao::total).isEqualTo(0L);
    }

    @Test
    @DisplayName("Dimensao desconhecida gera erro 400")
    void dimensaoInvalida() {
        assertThatThrownBy(() -> service.distribuicao("cor", filtro("2026-10-01", "2026-10-31")))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    @DisplayName("Ranking de tipos: posicao, percentual sobre o total e horas")
    void rankingTipos() {
        when(consultas.contar(any(), eq(DashboardConsultas.DATA_ABERTURA), eq(false))).thenReturn(200L);
        when(consultas.estatisticasPor(eq("tipo"), any(), any(), eq(false), anyInt())).thenReturn(List.of(
                new Estatistica("Hardware", 80, 70, 120.0, 4.0, 50, 20),
                new Estatistica("Rede e conectividade", 40, 30, null, null, 0, 0)));

        List<RankingTipo> r = service.rankingTipos(filtro("2026-10-01", "2026-10-31"), 5);

        assertThat(r.get(0).posicao()).isEqualTo(1);
        assertThat(r.get(0).percentual()).isEqualTo(40.0);
        assertThat(r.get(0).tempoMedioResolucaoHoras()).isEqualTo(2.0);
        assertThat(r.get(0).taxaSla()).isEqualTo(71.4);
        assertThat(r.get(1).taxaSla()).isNull();
    }

    @Test
    @DisplayName("Tecnico ausente aparece como 'Não atribuído'")
    void rankingTecnicoSemNome() {
        when(consultas.estatisticasPor(eq("tecnico"), any(), eq(DashboardConsultas.DATA_FECHAMENTO), eq(true), anyInt()))
                .thenReturn(List.of(new Estatistica(null, 3, 3, 60.0, null, 3, 0)));

        List<RankingTecnico> r = service.rankingTecnicos(filtro("2026-10-01", "2026-10-31"), 10);

        assertThat(r.get(0).tecnico()).isEqualTo("Não atribuído");
        assertThat(r.get(0).taxaSla()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Limite fora de 1..50 e recusado")
    void limiteInvalido() {
        FiltroDashboard f = filtro("2026-10-01", "2026-10-31");
        assertThatThrownBy(() -> service.rankingTipos(f, 0)).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> service.rankingTecnicos(f, 51)).isInstanceOf(ParametroInvalidoException.class);
    }
}
