package com.pfc.thindesk.dto;

import java.time.Instant;
import java.util.List;

/** Formato padrao de erro de toda a API. */
public record ErroResposta(
        Instant timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho,
        List<String> detalhes) {
}
