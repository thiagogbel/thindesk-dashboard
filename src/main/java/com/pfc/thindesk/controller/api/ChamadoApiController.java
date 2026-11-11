package com.pfc.thindesk.controller.api;

import com.pfc.thindesk.dto.AtualizarStatusRequest;
import com.pfc.thindesk.dto.NovoChamadoRequest;
import com.pfc.thindesk.dto.Pagina;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.service.ChamadoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CRUD de chamados: lista detalhada do dashboard e atualizacao dos dados (RF07). */
@RestController
@RequestMapping("/api/v1/chamados")
public class ChamadoApiController {

    private final ChamadoService service;
    private final ZoneId zona;

    public ChamadoApiController(ChamadoService service,
                                @Value("${thindesk.fuso-horario:America/Sao_Paulo}") String fuso) {
        this.service = service;
        this.zona = ZoneId.of(fuso);
    }

    @GetMapping
    public Pagina<Chamado> listar(
            @RequestParam(required = false) List<StatusChamado> status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String tecnico,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(defaultValue = "dataAbertura") String ordenarPor,
            @RequestParam(defaultValue = "desc") String direcao) {
        return service.listar(status, tipo, tecnico, busca,
                inicio == null ? null : inicio.atStartOfDay(zona).toInstant(),
                fim == null ? null : fim.plusDays(1).atStartOfDay(zona).toInstant(),
                pagina, tamanho, ordenarPor, direcao);
    }

    @GetMapping("/{id}")
    public Chamado buscar(@PathVariable String id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<Chamado> criar(@Valid @RequestBody NovoChamadoRequest req) {
        Chamado criado = service.criar(req);
        return ResponseEntity.created(URI.create("/api/v1/chamados/" + criado.getId())).body(criado);
    }

    @PatchMapping("/{id}/status")
    public Chamado atualizarStatus(@PathVariable String id, @Valid @RequestBody AtualizarStatusRequest req) {
        return service.atualizarStatus(id, req);
    }
}
