package com.pfc.thindesk.controller.api;

import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.Granularidade;
import com.pfc.thindesk.dto.ItemDistribuicao;
import com.pfc.thindesk.dto.OpcoesFiltro;
import com.pfc.thindesk.dto.RankingTecnico;
import com.pfc.thindesk.dto.RankingTipo;
import com.pfc.thindesk.dto.ResumoDashboard;
import com.pfc.thindesk.dto.SerieTemporal;
import com.pfc.thindesk.entity.Prioridade;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints que alimentam o Dashboard Administrativo (RF01-RF06).
 * Todos aceitam os mesmos filtros (RF05):
 *   inicio, fim (yyyy-MM-dd) | status (lista) | tipo | prioridade | tecnico | setor
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardApiController {

    private final DashboardService service;

    public DashboardApiController(DashboardService service) {
        this.service = service;
    }

    /** RF01 - Indicadores gerais. */
    @GetMapping("/resumo")
    public ResumoDashboard resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) List<StatusChamado> status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String setor) {
        return service.resumo(filtro(inicio, fim, status, tipo, prioridade, tecnico, setor));
    }

    /** RF02 - Chamados abertos x atendidos por periodo. */
    @GetMapping("/chamados-por-periodo")
    public SerieTemporal porPeriodo(
            @RequestParam(required = false, defaultValue = "AUTO") Granularidade granularidade,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) List<StatusChamado> status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String setor) {
        return service.seriePorPeriodo(filtro(inicio, fim, status, tipo, prioridade, tecnico, setor), granularidade);
    }

    /** RF03 - Situacao (status) dos chamados. */
    @GetMapping("/status")
    public List<ItemDistribuicao> status(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) List<StatusChamado> status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String setor) {
        return service.distribuicaoStatus(filtro(inicio, fim, status, tipo, prioridade, tecnico, setor));
    }

    /** Distribuicao por prioridade, setor, canal ou tipo. */
    @GetMapping("/distribuicao/{dimensao}")
    public List<ItemDistribuicao> distribuicao(
            @PathVariable String dimensao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) List<StatusChamado> status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String setor) {
        return service.distribuicao(dimensao, filtro(inicio, fim, status, tipo, prioridade, tecnico, setor));
    }

    /** RF04 - Tipos de chamados mais atendidos (ranking). */
    @GetMapping("/tipos")
    public List<RankingTipo> tipos(
            @RequestParam(required = false, defaultValue = "10") int limite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) List<StatusChamado> status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String setor) {
        return service.rankingTipos(filtro(inicio, fim, status, tipo, prioridade, tecnico, setor), limite);
    }

    /** Ranking de tecnicos por chamados atendidos no periodo. */
    @GetMapping("/tecnicos")
    public List<RankingTecnico> tecnicos(
            @RequestParam(required = false, defaultValue = "10") int limite,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) List<StatusChamado> status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String setor) {
        return service.rankingTecnicos(filtro(inicio, fim, status, tipo, prioridade, tecnico, setor), limite);
    }

    /** RF05 - Valores disponiveis para os filtros. */
    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return service.opcoesFiltro();
    }

    private FiltroDashboard filtro(LocalDate inicio, LocalDate fim, List<StatusChamado> status, String tipo,
                                   Prioridade prioridade, String tecnico, String setor) {
        return service.montarFiltro(inicio, fim, status, tipo, prioridade, tecnico, setor);
    }
}
