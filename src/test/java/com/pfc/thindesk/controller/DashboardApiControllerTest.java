package com.pfc.thindesk.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfc.thindesk.controller.api.DashboardApiController;
import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.Granularidade;
import com.pfc.thindesk.dto.RankingTipo;
import com.pfc.thindesk.dto.ResumoDashboard;
import com.pfc.thindesk.entity.Prioridade;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.ApiExceptionHandler;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import com.pfc.thindesk.service.DashboardService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Testa o contrato HTTP da API (parametros, JSON e codigos de erro). */
class DashboardApiControllerTest {

    private DashboardService service;
    private MockMvc mvc;
    private final FiltroDashboard filtro = new FiltroDashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
            Instant.parse("2026-10-01T03:00:00Z"), Instant.parse("2026-11-01T03:00:00Z"), List.of(), null, null, null, null);

    @BeforeEach
    void setUp() {
        service = mock(DashboardService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DashboardApiController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /resumo retorna 200 com os indicadores")
    void resumoOk() throws Exception {
        when(service.montarFiltro(any(), any(), any(), any(), any(), any(), any())).thenReturn(filtro);
        when(service.resumo(filtro)).thenReturn(new ResumoDashboard(filtro.inicio(), filtro.fim(), 1200, 1000, 20.0,
                30, 50, 10, 100, 980, 30, 90, 12, 1050, 82.5, 6.4, 4.3, Instant.parse("2026-11-11T17:00:00Z")));

        mvc.perform(get("/api/v1/dashboard/resumo").param("inicio", "2026-10-01").param("fim", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAbertosNoPeriodo").value(1200))
                .andExpect(jsonPath("$.backlog").value(90))
                .andExpect(jsonPath("$.taxaSla").value(82.5));
    }

    @Test
    @DisplayName("Filtros da URL chegam convertidos ao servico (lista de status, enum, datas)")
    void conversaoDosFiltros() throws Exception {
        when(service.montarFiltro(any(), any(), any(), any(), any(), any(), any())).thenReturn(filtro);

        mvc.perform(get("/api/v1/dashboard/status")
                        .param("inicio", "2026-10-01").param("fim", "2026-10-31")
                        .param("status", "ABERTO,EM_ANDAMENTO").param("prioridade", "ALTA").param("tipo", "Hardware"))
                .andExpect(status().isOk());

        verify(service).montarFiltro(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 31)),
                eq(List.of(StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO)), eq("Hardware"), eq(Prioridade.ALTA),
                isNull(), isNull());
    }

    @Test
    @DisplayName("Data em formato invalido retorna 400 no formato padrao de erro")
    void dataInvalida() throws Exception {
        mvc.perform(get("/api/v1/dashboard/resumo").param("inicio", "31/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("inicio")))
                .andExpect(jsonPath("$.caminho").value("/api/v1/dashboard/resumo"));
    }

    @Test
    @DisplayName("Granularidade desconhecida retorna 400")
    void granularidadeInvalida() throws Exception {
        mvc.perform(get("/api/v1/dashboard/chamados-por-periodo").param("granularidade", "ANO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("granularidade")));
    }

    @Test
    @DisplayName("Status desconhecido retorna 400")
    void statusInvalido() throws Exception {
        mvc.perform(get("/api/v1/dashboard/resumo").param("status", "PENDENTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Regra de periodo violada no servico retorna 400 com a mensagem")
    void periodoInvalido() throws Exception {
        when(service.montarFiltro(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ParametroInvalidoException("A data inicial (2026-12-01) não pode ser maior que a data final (2026-01-01)"));

        mvc.perform(get("/api/v1/dashboard/resumo").param("inicio", "2026-12-01").param("fim", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Bad Request"))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("não pode ser maior")));
    }

    @Test
    @DisplayName("GET /tipos usa limite padrao 10 e devolve o ranking")
    void rankingTipos() throws Exception {
        when(service.montarFiltro(any(), any(), any(), any(), any(), any(), any())).thenReturn(filtro);
        when(service.rankingTipos(filtro, 10)).thenReturn(List.of(new RankingTipo(1, "Acesso e senha", 300, 290, 25.0, 1.2, 95.0)));

        mvc.perform(get("/api/v1/dashboard/tipos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("Acesso e senha"))
                .andExpect(jsonPath("$[0].posicao").value(1));
    }

    @Test
    @DisplayName("Falha inesperada retorna 500 sem expor detalhes internos")
    void erroInterno() throws Exception {
        when(service.montarFiltro(any(), any(), any(), any(), any(), any(), any())).thenReturn(filtro);
        when(service.seriePorPeriodo(any(), any(Granularidade.class))).thenThrow(new IllegalStateException("conexão recusada"));

        mvc.perform(get("/api/v1/dashboard/chamados-por-periodo"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value("Erro interno ao processar a requisição"));
    }

    @Test
    @DisplayName("Limite invalido no ranking de tecnicos retorna 400")
    void limiteInvalido() throws Exception {
        when(service.montarFiltro(any(), any(), any(), any(), any(), any(), any())).thenReturn(filtro);
        when(service.rankingTecnicos(eq(filtro), anyInt())).thenThrow(new ParametroInvalidoException("O parâmetro 'limite' deve estar entre 1 e 50"));

        mvc.perform(get("/api/v1/dashboard/tecnicos").param("limite", "500"))
                .andExpect(status().isBadRequest());
    }
}
