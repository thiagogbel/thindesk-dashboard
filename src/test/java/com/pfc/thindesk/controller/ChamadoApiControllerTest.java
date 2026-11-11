package com.pfc.thindesk.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfc.thindesk.controller.api.ChamadoApiController;
import com.pfc.thindesk.dto.AtualizarStatusRequest;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.ApiExceptionHandler;
import com.pfc.thindesk.exception.RecursoNaoEncontradoException;
import com.pfc.thindesk.exception.RegraNegocioException;
import com.pfc.thindesk.service.ChamadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ChamadoApiControllerTest {

    private ChamadoService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ChamadoService.class);
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ChamadoApiController(service, "America/Sao_Paulo"))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validador)
                .build();
    }

    @Test
    @DisplayName("GET /chamados/{id} inexistente retorna 404")
    void naoEncontrado() throws Exception {
        when(service.buscar("abc")).thenThrow(new RecursoNaoEncontradoException("Chamado não encontrado: abc"));

        mvc.perform(get("/api/v1/chamados/abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Chamado não encontrado: abc"));
    }

    @Test
    @DisplayName("POST com campos obrigatorios ausentes retorna 400 com a lista de erros")
    void criarInvalido() throws Exception {
        mvc.perform(post("/api/v1/chamados").contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes.length()").value(4)); // clienteId, prioridade, tipo, titulo
    }

    @Test
    @DisplayName("POST valido retorna 201 com Location")
    void criarValido() throws Exception {
        Chamado c = new Chamado();
        c.setId("novo1");
        c.setProtocolo("TD-2026-030001");
        when(service.criar(any())).thenReturn(c);

        mvc.perform(post("/api/v1/chamados").contentType(MediaType.APPLICATION_JSON).content("""
                        {"titulo":"Monitor não liga","tipo":"Hardware","prioridade":"MEDIA","clienteId":"cli1"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/chamados/novo1"))
                .andExpect(jsonPath("$.protocolo").value("TD-2026-030001"));
    }

    @Test
    @DisplayName("PATCH com JSON malformado retorna 400")
    void jsonMalformado() throws Exception {
        mvc.perform(patch("/api/v1/chamados/c1/status").contentType(MediaType.APPLICATION_JSON).content("{status:"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH com avaliacao fora de 1..5 retorna 400")
    void avaliacaoForaDoIntervalo() throws Exception {
        mvc.perform(patch("/api/v1/chamados/c1/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FECHADO\",\"avaliacao\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes[0]").value("avaliacao: avaliacao máxima é 5"));
    }

    @Test
    @DisplayName("PATCH com transicao proibida retorna 422")
    void transicaoProibida() throws Exception {
        when(service.atualizarStatus(eq("c1"), any(AtualizarStatusRequest.class)))
                .thenThrow(new RegraNegocioException("Transição de status não permitida: Aberto → Fechado"));

        mvc.perform(patch("/api/v1/chamados/c1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FECHADO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    @DisplayName("PATCH valido retorna o chamado atualizado")
    void atualizarOk() throws Exception {
        Chamado c = new Chamado();
        c.setId("c1");
        c.setStatus(StatusChamado.EM_ANDAMENTO);
        when(service.atualizarStatus(eq("c1"), any(AtualizarStatusRequest.class))).thenReturn(c);

        mvc.perform(patch("/api/v1/chamados/c1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EM_ANDAMENTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"));
    }

    @Test
    @DisplayName("Tamanho de pagina invalido e repassado como erro 400")
    void paginacaoInvalida() throws Exception {
        when(service.listar(any(), any(), any(), any(), any(), any(), eq(0), eq(1000), any(), any()))
                .thenThrow(new com.pfc.thindesk.exception.ParametroInvalidoException("O parâmetro 'tamanho' deve estar entre 1 e 100"));

        mvc.perform(get("/api/v1/chamados").param("tamanho", "1000"))
                .andExpect(status().isBadRequest());
    }
}
