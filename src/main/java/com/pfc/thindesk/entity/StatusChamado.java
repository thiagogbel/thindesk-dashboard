package com.pfc.thindesk.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de um chamado.
 *
 * ABERTO -> EM_ANDAMENTO -> (AGUARDANDO_CLIENTE) -> RESOLVIDO -> FECHADO
 * Qualquer estado nao finalizado pode ir para CANCELADO.
 * RESOLVIDO pode ser reaberto (volta para EM_ANDAMENTO).
 */
public enum StatusChamado {

    ABERTO("Aberto"),
    EM_ANDAMENTO("Em andamento"),
    AGUARDANDO_CLIENTE("Aguardando cliente"),
    RESOLVIDO("Resolvido"),
    FECHADO("Fechado"),
    CANCELADO("Cancelado");

    private final String label;

    StatusChamado(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Chamado que ja recebeu solucao (conta como "atendido"). */
    public boolean isAtendido() {
        return this == RESOLVIDO || this == FECHADO;
    }

    /** Chamado que saiu da fila (atendido ou cancelado). */
    public boolean isFinalizado() {
        return isAtendido() || this == CANCELADO;
    }

    /** Estados que compoem o backlog (fila de trabalho). */
    public static Set<StatusChamado> emAberto() {
        return EnumSet.of(ABERTO, EM_ANDAMENTO, AGUARDANDO_CLIENTE);
    }

    public Set<StatusChamado> proximosPermitidos() {
        return switch (this) {
            case ABERTO -> EnumSet.of(EM_ANDAMENTO, CANCELADO);
            case EM_ANDAMENTO -> EnumSet.of(AGUARDANDO_CLIENTE, RESOLVIDO, CANCELADO);
            case AGUARDANDO_CLIENTE -> EnumSet.of(EM_ANDAMENTO, RESOLVIDO, CANCELADO);
            case RESOLVIDO -> EnumSet.of(FECHADO, EM_ANDAMENTO);
            case FECHADO, CANCELADO -> EnumSet.noneOf(StatusChamado.class);
        };
    }

    public boolean podeIrPara(StatusChamado novo) {
        return novo != null && proximosPermitidos().contains(novo);
    }
}
