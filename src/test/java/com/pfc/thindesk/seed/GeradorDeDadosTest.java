package com.pfc.thindesk.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.entity.Tecnico;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Garante que a base gerada e coerente (os indicadores do dashboard dependem disso). */
class GeradorDeDadosTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T17:00:00Z");
    private static List<Chamado> chamados;
    private static List<Cliente> clientes;

    @BeforeAll
    static void gerar() {
        GeradorDeDados g = new GeradorDeDados(2026L, ZoneId.of("America/Sao_Paulo"));
        List<Tecnico> tecnicos = g.tecnicos(40);
        clientes = g.clientes(1500, AGORA);
        chamados = g.chamados(5000, 18, clientes, tecnicos, AGORA);
    }

    @Test
    @DisplayName("Quantidades e unicidade (protocolo e e-mail)")
    void quantidades() {
        assertThat(chamados).hasSize(5000);
        assertThat(new HashSet<>(chamados.stream().map(Chamado::getProtocolo).toList())).hasSize(5000);
        assertThat(new HashSet<>(clientes.stream().map(Cliente::getEmail).toList())).hasSize(1500);
    }

    @Test
    @DisplayName("Nenhuma data no futuro e fechamento sempre apos a abertura")
    void datasCoerentes() {
        for (Chamado c : chamados) {
            assertThat(c.getDataAbertura()).isBeforeOrEqualTo(AGORA);
            if (c.getDataFechamento() != null) {
                assertThat(c.getDataFechamento()).isAfterOrEqualTo(c.getDataAbertura()).isBeforeOrEqualTo(AGORA);
            }
        }
    }

    @Test
    @DisplayName("Atendidos tem tempo de resolucao e SLA coerente com o prazo")
    void slaCoerente() {
        for (Chamado c : chamados) {
            if (c.getStatus().isAtendido()) {
                assertThat(c.getTempoResolucaoMin()).isNotNull();
                assertThat(c.getSlaCumprido()).isEqualTo(!c.getDataFechamento().isAfter(c.getPrazoSla()));
                assertThat(c.getTecnico()).isNotBlank();
            } else {
                assertThat(c.getSlaCumprido()).isNull();
            }
            if (c.getStatus() == StatusChamado.ABERTO) {
                assertThat(c.getTecnico()).isNull();
            }
        }
    }

    @Test
    @DisplayName("Mesma semente gera os mesmos dados")
    void deterministico() {
        GeradorDeDados g = new GeradorDeDados(2026L, ZoneId.of("America/Sao_Paulo"));
        List<Tecnico> t = g.tecnicos(40);
        List<Cliente> cl = g.clientes(1500, AGORA);
        List<Chamado> outra = g.chamados(5000, 18, cl, t, AGORA);
        assertThat(outra.get(1234).getTitulo()).isEqualTo(chamados.get(1234).getTitulo());
        assertThat(outra.get(1234).getDataAbertura()).isEqualTo(chamados.get(1234).getDataAbertura());
    }
}
