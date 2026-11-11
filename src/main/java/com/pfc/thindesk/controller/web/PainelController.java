package com.pfc.thindesk.controller.web;

import com.pfc.thindesk.dto.AtualizarStatusRequest;
import com.pfc.thindesk.dto.Pagina;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.RegraNegocioException;
import com.pfc.thindesk.service.ChamadoService;
import com.pfc.thindesk.service.ClienteService;
import com.pfc.thindesk.service.DashboardService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Paginas Thymeleaf: dashboard, lista de chamados e clientes. */
@Controller
public class PainelController {

    private final DashboardService dashboard;
    private final ChamadoService chamados;
    private final ClienteService clientes;

    public PainelController(DashboardService dashboard, ChamadoService chamados, ClienteService clientes) {
        this.dashboard = dashboard;
        this.chamados = chamados;
        this.clientes = clientes;
    }

    /** Dados comuns a todas as paginas (topo e menu). */
    @ModelAttribute
    public void comum(Model model, Authentication auth) {
        if (auth != null) {
            model.addAttribute("usuarioLogado", auth.getName());
            model.addAttribute("ehGestor", auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER")));
        }
    }

    /**
     * O HTML e renderizado pelo Thymeleaf com as opcoes de filtro;
     * indicadores e graficos sao carregados via fetch() na API (RF06).
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pagina", "dashboard");
        model.addAttribute("opcoes", dashboard.opcoesFiltro());
        model.addAttribute("totalClientes", clientes.total());
        return "dashboard";
    }

    @GetMapping("/chamados")
    public String chamados(Model model,
                           @RequestParam(required = false) List<StatusChamado> status,
                           @RequestParam(required = false) String tipo,
                           @RequestParam(required = false) String busca,
                           @RequestParam(defaultValue = "0") int pagina) {
        Pagina<Chamado> resultado = chamados.listar(status, tipo, null, busca, null, null,
                Math.max(0, pagina), 25, "dataAbertura", "desc");
        model.addAttribute("pagina", "chamados");
        model.addAttribute("resultado", resultado);
        model.addAttribute("statusSelecionado", status == null || status.isEmpty() ? null : status.get(0));
        model.addAttribute("tipo", tipo);
        model.addAttribute("busca", busca);
        model.addAttribute("todosStatus", StatusChamado.values());
        model.addAttribute("tipos", dashboard.opcoesFiltro().tipos());
        return "chamados";
    }

    @PostMapping("/chamados/{id}/status")
    public String atualizarStatus(@PathVariable String id,
                                  @RequestParam StatusChamado status,
                                  @RequestParam(required = false) String consideracaoTecnica,
                                  @RequestParam(required = false) Integer avaliacao) {
        try {
            Chamado c = chamados.atualizarStatus(id, new AtualizarStatusRequest(status, consideracaoTecnica, avaliacao));
            return "redirect:/chamados?busca=" + URLEncoder.encode(c.getProtocolo(), StandardCharsets.UTF_8) + "&ok=true";
        } catch (RegraNegocioException e) {
            return "redirect:/chamados?erro=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/clientes")
    public String clientes(Model model,
                           @RequestParam(required = false) String nome,
                           @RequestParam(defaultValue = "0") int pagina) {
        Pagina<Cliente> resultado = clientes.listar(nome, Math.max(0, pagina), 25);
        model.addAttribute("pagina", "clientes");
        model.addAttribute("resultado", resultado);
        model.addAttribute("nome", nome);
        return "clientes";
    }
}
