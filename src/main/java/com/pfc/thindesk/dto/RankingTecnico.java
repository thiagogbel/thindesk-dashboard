package com.pfc.thindesk.dto;

/** Desempenho dos tecnicos no periodo. */
public record RankingTecnico(
        int posicao,
        String tecnico,
        long atendidos,
        Double tempoMedioResolucaoHoras,
        Double taxaSla,
        Double avaliacaoMedia) {
}
