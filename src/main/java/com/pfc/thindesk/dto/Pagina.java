package com.pfc.thindesk.dto;

import java.util.List;

/** Envelope de paginacao estavel para a API (evita serializar PageImpl). */
public record Pagina<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {

    public static <T> Pagina<T> of(List<T> conteudo, int pagina, int tamanho, long total) {
        int paginas = tamanho == 0 ? 0 : (int) Math.ceil((double) total / tamanho);
        return new Pagina<>(conteudo, pagina, tamanho, total, paginas);
    }
}
