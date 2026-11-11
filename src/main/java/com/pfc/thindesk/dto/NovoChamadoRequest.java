package com.pfc.thindesk.dto;

import com.pfc.thindesk.entity.Prioridade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo do POST /api/v1/chamados. */
public record NovoChamadoRequest(
        @NotBlank(message = "titulo é obrigatório") @Size(max = 120, message = "titulo deve ter no máximo 120 caracteres") String titulo,
        @Size(max = 2000, message = "descricao deve ter no máximo 2000 caracteres") String descricao,
        @NotBlank(message = "tipo é obrigatório") String tipo,
        @NotNull(message = "prioridade é obrigatória") Prioridade prioridade,
        @NotBlank(message = "clienteId é obrigatório") String clienteId,
        String tecnicoId,
        String canal) {
}
