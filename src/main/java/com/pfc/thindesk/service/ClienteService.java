package com.pfc.thindesk.service;

import com.pfc.thindesk.dto.Pagina;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import com.pfc.thindesk.repository.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    public Pagina<Cliente> listar(String nome, int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new ParametroInvalidoException("Use pagina >= 0 e tamanho entre 1 e 100");
        }
        PageRequest pr = PageRequest.of(pagina, tamanho, Sort.by("nome"));
        String filtro = DashboardService.limpar(nome);
        Page<Cliente> p = filtro == null ? clientes.findAll(pr) : clientes.findByNomeContainingIgnoreCase(filtro, pr);
        return Pagina.of(p.getContent(), pagina, tamanho, p.getTotalElements());
    }

    public long total() {
        return clientes.count();
    }
}
