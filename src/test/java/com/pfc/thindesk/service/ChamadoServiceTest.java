package com.pfc.thindesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfc.thindesk.dto.AtualizarStatusRequest;
import com.pfc.thindesk.dto.NovoChamadoRequest;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.entity.Prioridade;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.RecursoNaoEncontradoException;
import com.pfc.thindesk.exception.RegraNegocioException;
import com.pfc.thindesk.repository.ChamadoRepository;
import com.pfc.thindesk.repository.ClienteRepository;
import com.pfc.thindesk.repository.TecnicoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class ChamadoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T17:00:00Z");

    private ChamadoRepository chamados;
    private ClienteRepository clientes;
    private ChamadoService service;

    @BeforeEach
    void setUp() {
        chamados = mock(ChamadoRepository.class);
        clientes = mock(ClienteRepository.class);
        service = new ChamadoService(chamados, clientes, mock(TecnicoRepository.class), mock(MongoTemplate.class),
                Clock.fixed(AGORA, ZoneOffset.UTC), "America/Sao_Paulo");
        when(chamados.save(any(Chamado.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Chamado chamado(StatusChamado status, Duration idade, Prioridade prioridade) {
        Chamado c = new Chamado();
        c.setId("c1");
        c.setProtocolo("TD-2026-000001");
        c.setStatus(status);
        c.setPrioridade(prioridade);
        c.setDataAbertura(AGORA.minus(idade));
        c.setPrazoSla(c.getDataAbertura().plus(Duration.ofHours(prioridade.getSlaHoras())));
        when(chamados.findById("c1")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    @DisplayName("Iniciar atendimento registra a data do primeiro atendimento")
    void iniciarAtendimento() {
        chamado(StatusChamado.ABERTO, Duration.ofHours(1), Prioridade.MEDIA);

        Chamado c = service.atualizarStatus("c1", new AtualizarStatusRequest(StatusChamado.EM_ANDAMENTO, null, null));

        assertThat(c.getStatus()).isEqualTo(StatusChamado.EM_ANDAMENTO);
        assertThat(c.getDataPrimeiroAtendimento()).isEqualTo(AGORA);
        assertThat(c.getUltimaAtualizacao()).isEqualTo(AGORA);
    }

    @Test
    @DisplayName("Resolver dentro do prazo: calcula tempo de resolucao e SLA cumprido")
    void resolverDentroDoSla() {
        chamado(StatusChamado.EM_ANDAMENTO, Duration.ofHours(3), Prioridade.ALTA); // SLA 8h

        Chamado c = service.atualizarStatus("c1",
                new AtualizarStatusRequest(StatusChamado.RESOLVIDO, "  Cabo de rede substituído  ", null));

        assertThat(c.getDataFechamento()).isEqualTo(AGORA);
        assertThat(c.getTempoResolucaoMin()).isEqualTo(180L);
        assertThat(c.getSlaCumprido()).isTrue();
        assertThat(c.getConsideracaoTecnica()).isEqualTo("Cabo de rede substituído");
    }

    @Test
    @DisplayName("Resolver fora do prazo marca SLA como nao cumprido")
    void resolverForaDoSla() {
        chamado(StatusChamado.EM_ANDAMENTO, Duration.ofHours(10), Prioridade.CRITICA); // SLA 4h

        Chamado c = service.atualizarStatus("c1", new AtualizarStatusRequest(StatusChamado.RESOLVIDO, "Servidor reiniciado", null));

        assertThat(c.getSlaCumprido()).isFalse();
    }

    @Test
    @DisplayName("Resolver sem consideracao tecnica gera erro 422")
    void resolverSemConsideracao() {
        chamado(StatusChamado.EM_ANDAMENTO, Duration.ofHours(1), Prioridade.MEDIA);

        assertThatThrownBy(() -> service.atualizarStatus("c1", new AtualizarStatusRequest(StatusChamado.RESOLVIDO, " ", null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("consideração técnica");
        verify(chamados, never()).save(any());
    }

    @Test
    @DisplayName("Transicao proibida (Aberto -> Fechado) gera erro 422")
    void transicaoProibida() {
        chamado(StatusChamado.ABERTO, Duration.ofHours(1), Prioridade.MEDIA);

        assertThatThrownBy(() -> service.atualizarStatus("c1", new AtualizarStatusRequest(StatusChamado.FECHADO, null, null)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não permitida");
    }

    @Test
    @DisplayName("Avaliacao so e aceita ao fechar o chamado")
    void avaliacaoSomenteNoFechamento() {
        chamado(StatusChamado.EM_ANDAMENTO, Duration.ofHours(1), Prioridade.MEDIA);

        assertThatThrownBy(() -> service.atualizarStatus("c1", new AtualizarStatusRequest(StatusChamado.RESOLVIDO, "ok", 5)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Fechar um chamado resolvido grava a avaliacao e mantem a data de solucao")
    void fecharComAvaliacao() {
        Chamado original = chamado(StatusChamado.RESOLVIDO, Duration.ofHours(30), Prioridade.BAIXA);
        Instant solucao = AGORA.minus(Duration.ofHours(5));
        original.setDataFechamento(solucao);

        Chamado c = service.atualizarStatus("c1", new AtualizarStatusRequest(StatusChamado.FECHADO, null, 4));

        assertThat(c.getAvaliacao()).isEqualTo(4);
        assertThat(c.getDataFechamento()).isEqualTo(solucao);
    }

    @Test
    @DisplayName("Reabrir um chamado resolvido limpa os dados de fechamento")
    void reabrir() {
        Chamado original = chamado(StatusChamado.RESOLVIDO, Duration.ofHours(30), Prioridade.BAIXA);
        original.setDataFechamento(AGORA.minus(Duration.ofHours(2)));
        original.setTempoResolucaoMin(1680L);
        original.setSlaCumprido(true);

        Chamado c = service.atualizarStatus("c1", new AtualizarStatusRequest(StatusChamado.EM_ANDAMENTO, "Problema voltou", null));

        assertThat(c.getDataFechamento()).isNull();
        assertThat(c.getTempoResolucaoMin()).isNull();
        assertThat(c.getSlaCumprido()).isNull();
    }

    @Test
    @DisplayName("Chamado inexistente gera erro 404")
    void chamadoInexistente() {
        when(chamados.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buscar("x")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Novo chamado: protocolo, prazo de SLA e dados do cliente")
    void criarChamado() {
        Cliente cli = new Cliente();
        cli.setId("cli1");
        cli.setNome("Maria Souza");
        cli.setSetor("Financeiro");
        when(clientes.findById("cli1")).thenReturn(Optional.of(cli));
        when(chamados.count()).thenReturn(30000L);

        Chamado c = service.criar(new NovoChamadoRequest(" Impressora parada ", null, "Impressora", Prioridade.ALTA, "cli1", null, null));

        assertThat(c.getProtocolo()).isEqualTo("TD-2026-030001");
        assertThat(c.getTitulo()).isEqualTo("Impressora parada");
        assertThat(c.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(c.getPrazoSla()).isEqualTo(AGORA.plus(Duration.ofHours(8)));
        assertThat(c.getUsuario()).isEqualTo("Maria Souza");
        assertThat(c.getSetor()).isEqualTo("Financeiro");
        assertThat(c.getCanal()).isEqualTo("Portal");
    }

    @Test
    @DisplayName("Novo chamado com cliente inexistente gera erro 404")
    void criarComClienteInexistente() {
        when(clientes.findById("nao-existe")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.criar(new NovoChamadoRequest("t", null, "Hardware", Prioridade.BAIXA, "nao-existe", null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
