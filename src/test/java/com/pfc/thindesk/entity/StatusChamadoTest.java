package com.pfc.thindesk.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusChamadoTest {

    @Test
    @DisplayName("Fluxo normal: aberto -> em andamento -> resolvido -> fechado")
    void fluxoNormal() {
        assertThat(StatusChamado.ABERTO.podeIrPara(StatusChamado.EM_ANDAMENTO)).isTrue();
        assertThat(StatusChamado.EM_ANDAMENTO.podeIrPara(StatusChamado.RESOLVIDO)).isTrue();
        assertThat(StatusChamado.RESOLVIDO.podeIrPara(StatusChamado.FECHADO)).isTrue();
    }

    @Test
    @DisplayName("Transicoes invalidas sao bloqueadas")
    void transicoesInvalidas() {
        assertThat(StatusChamado.ABERTO.podeIrPara(StatusChamado.FECHADO)).isFalse();
        assertThat(StatusChamado.FECHADO.podeIrPara(StatusChamado.ABERTO)).isFalse();
        assertThat(StatusChamado.CANCELADO.proximosPermitidos()).isEmpty();
        assertThat(StatusChamado.ABERTO.podeIrPara(null)).isFalse();
    }

    @Test
    @DisplayName("Classificacao: atendido, finalizado e backlog")
    void classificacao() {
        assertThat(StatusChamado.RESOLVIDO.isAtendido()).isTrue();
        assertThat(StatusChamado.CANCELADO.isAtendido()).isFalse();
        assertThat(StatusChamado.CANCELADO.isFinalizado()).isTrue();
        assertThat(StatusChamado.emAberto())
                .containsExactlyInAnyOrder(StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO, StatusChamado.AGUARDANDO_CLIENTE);
    }
}
