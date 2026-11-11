package com.pfc.thindesk.controller.api;

import com.pfc.thindesk.dto.Pagina;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.entity.Tecnico;
import com.pfc.thindesk.repository.TecnicoRepository;
import com.pfc.thindesk.service.ClienteService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de clientes e tecnicos (apoio aos filtros e a abertura de chamados). */
@RestController
@RequestMapping("/api/v1")
public class CadastroApiController {

    private final ClienteService clientes;
    private final TecnicoRepository tecnicos;

    public CadastroApiController(ClienteService clientes, TecnicoRepository tecnicos) {
        this.clientes = clientes;
        this.tecnicos = tecnicos;
    }

    @GetMapping("/clientes")
    public Pagina<Cliente> clientes(@RequestParam(required = false) String nome,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "20") int tamanho) {
        return clientes.listar(nome, pagina, tamanho);
    }

    @GetMapping("/tecnicos")
    public List<Tecnico> tecnicos() {
        return tecnicos.findByAtivoTrueOrderByNomeAsc();
    }
}
