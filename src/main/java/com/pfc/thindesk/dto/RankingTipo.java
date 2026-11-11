package com.pfc.thindesk.dto;

/** Tipos de chamado mais atendidos (RF04). */
public record RankingTipo(
        int posicao,
        String tipo,
        long total,
        long atendidos,
        double percentual,
        Double tempoMedioResolucaoHoras,
        Double taxaSla) {
}
