package com.pfc.thindesk.dto;

import java.time.LocalDate;
import java.util.List;

/** Valores disponiveis para os filtros do dashboard (RF05). */
public record OpcoesFiltro(
        List<OpcaoFiltro> status,
        List<OpcaoFiltro> prioridades,
        List<String> tipos,
        List<String> tecnicos,
        List<String> setores,
        LocalDate dataMinima,
        LocalDate dataMaxima) {
}
