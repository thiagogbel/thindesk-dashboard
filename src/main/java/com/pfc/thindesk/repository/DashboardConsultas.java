package com.pfc.thindesk.repository;

import com.pfc.thindesk.dto.FiltroDashboard;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Consultas agregadas usadas pelo dashboard.
 * A implementacao usa o Aggregation Framework do MongoDB, ou seja, o
 * calculo e feito no banco e so o resultado trafega para a aplicacao.
 */
public interface DashboardConsultas {

    /** Base de abertura: chamados abertos no periodo do filtro. */
    String DATA_ABERTURA = "dataAbertura";
    /** Base de atendimento: chamados resolvidos/fechados no periodo do filtro. */
    String DATA_FECHAMENTO = "dataFechamento";

    /** Contagem total aplicando o filtro sobre a data informada. */
    long contar(FiltroDashboard filtro, String campoData, boolean somenteAtendidos);

    /** Chamados em aberto (backlog) com prazo de SLA vencido antes de "agora". */
    long contarAtrasados(FiltroDashboard filtro, Instant agora);

    /** Contagem agrupada por um campo (status, prioridade, setor, canal...). */
    Map<String, Long> contarPor(String campo, FiltroDashboard filtro, String campoData, boolean somenteAtendidos);

    /** Contagem agrupada por periodo ($dateToString com o formato informado). */
    Map<String, Long> contarPorPeriodo(String formatoMongo, FiltroDashboard filtro, String campoData, boolean somenteAtendidos, String fuso);

    /** Estatisticas agrupadas por campo, ordenadas por total desc. */
    List<Estatistica> estatisticasPor(String campo, FiltroDashboard filtro, String campoData, boolean somenteAtendidos, int limite);

    /** Estatisticas gerais (sem agrupamento). */
    Estatistica estatisticasGerais(FiltroDashboard filtro, String campoData, boolean somenteAtendidos);

    List<String> valoresDistintos(String campo);

    LocalDate dataMinima(String fuso);

    LocalDate dataMaxima(String fuso);

    /**
     * Resultado de agregacao.
     * tempoMedioMin e avaliacaoMedia podem ser nulos (nenhum chamado atendido).
     */
    record Estatistica(String chave, long total, long atendidos, Double tempoMedioMin,
                       Double avaliacaoMedia, long dentroSla, long foraSla) {
    }
}
