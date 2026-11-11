package com.pfc.thindesk.dto;

import com.pfc.thindesk.entity.Prioridade;
import com.pfc.thindesk.entity.StatusChamado;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Filtros ja validados e normalizados (RF05).
 * inicio/fim sao datas locais (fuso do sistema); inicioUtc/fimUtc sao os
 * instantes usados na consulta (fimUtc e exclusivo: 00:00 do dia seguinte).
 */
public record FiltroDashboard(
        LocalDate inicio,
        LocalDate fim,
        Instant inicioUtc,
        Instant fimUtc,
        List<StatusChamado> status,
        String tipo,
        Prioridade prioridade,
        String tecnico,
        String setor) {

    public FiltroDashboard {
        status = status == null ? List.of() : List.copyOf(status);
    }

    public long dias() {
        return java.time.temporal.ChronoUnit.DAYS.between(inicio, fim) + 1;
    }
}
