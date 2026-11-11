package com.pfc.thindesk.dto;

import com.pfc.thindesk.entity.StatusChamado;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo do PATCH /api/v1/chamados/{id}/status (RF07). */
public record AtualizarStatusRequest(
        @NotNull(message = "status é obrigatório") StatusChamado status,
        @Size(max = 2000, message = "consideracaoTecnica deve ter no máximo 2000 caracteres") String consideracaoTecnica,
        @Min(value = 1, message = "avaliacao mínima é 1") @Max(value = 5, message = "avaliacao máxima é 5") Integer avaliacao) {
}
