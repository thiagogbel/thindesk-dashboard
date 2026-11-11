package com.pfc.thindesk.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Indicadores gerais (RF01). */
public record ResumoDashboard(
        LocalDate inicio,
        LocalDate fim,
        long totalAbertosNoPeriodo,
        long totalPeriodoAnterior,
        Double variacaoPercentual,
        long abertos,
        long emAndamento,
        long aguardandoCliente,
        long resolvidos,
        long fechados,
        long cancelados,
        long backlog,
        long atrasados,
        long atendidosNoPeriodo,
        Double taxaSla,
        Double tempoMedioResolucaoHoras,
        Double avaliacaoMedia,
        Instant geradoEm) {
}
