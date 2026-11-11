package com.pfc.thindesk.dto;

/** Contagem por categoria (status, prioridade, setor, canal). */
public record ItemDistribuicao(String chave, String rotulo, long total, double percentual) {
}
