package com.pfc.thindesk.dto;

/** Um ponto da serie: chave do periodo (ex.: 2026-03, 2026-W10, 2026-03-15). */
public record PontoSerie(String periodo, String rotulo, long abertos, long atendidos) {
}
