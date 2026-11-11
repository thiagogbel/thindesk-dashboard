package com.pfc.thindesk.dto;

import java.time.LocalDate;
import java.util.List;

public record SerieTemporal(
        Granularidade granularidade,
        LocalDate inicio,
        LocalDate fim,
        long totalAbertos,
        long totalAtendidos,
        List<PontoSerie> pontos) {
}
