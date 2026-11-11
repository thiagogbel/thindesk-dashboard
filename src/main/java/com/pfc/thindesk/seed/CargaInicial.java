package com.pfc.thindesk.seed;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.Cliente;
import com.pfc.thindesk.entity.Tecnico;
import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Popula o banco com uma base grande para o dashboard
 * (padrao: 1.500 clientes, 40 tecnicos, 30.000 chamados em 18 meses).
 * So executa se a colecao "chamados" estiver vazia, ou se thindesk.seed.reset=true.
 */
@Component
@Order(2)
public class CargaInicial implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaInicial.class);
    private static final int LOTE = 2000;

    private final MongoTemplate mongo;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @Value("${thindesk.seed.enabled:true}") private boolean habilitado;
    @Value("${thindesk.seed.reset:false}") private boolean reset;
    @Value("${thindesk.seed.clientes:1500}") private int qtdClientes;
    @Value("${thindesk.seed.tecnicos:40}") private int qtdTecnicos;
    @Value("${thindesk.seed.chamados:30000}") private int qtdChamados;
    @Value("${thindesk.seed.meses:18}") private int meses;
    @Value("${thindesk.fuso-horario:America/Sao_Paulo}") private String fuso;
    @Value("${THINDESK_ADMIN_PASSWORD:Admin@123}") private String senhaAdmin;

    public CargaInicial(MongoTemplate mongo, UsuarioRepository usuarios, PasswordEncoder encoder, Clock clock) {
        this.mongo = mongo;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (!habilitado) {
            return;
        }
        if (reset) {
            log.warn("thindesk.seed.reset=true: apagando chamados, clientes e tecnicos");
            // remove (em vez de drop) para manter os indices criados na inicializacao
            mongo.remove(new Query(), Chamado.class);
            mongo.remove(new Query(), Cliente.class);
            mongo.remove(new Query(), Tecnico.class);
        }
        if (mongo.count(new Query(), Chamado.class) > 0) {
            garantirUsuariosBase();
            log.info("Base de chamados ja existente - carga inicial ignorada");
            return;
        }

        long t0 = System.currentTimeMillis();
        Instant agora = clock.instant();
        GeradorDeDados gerador = new GeradorDeDados(2026L, ZoneId.of(fuso));

        List<Tecnico> tecnicos = gerador.tecnicos(qtdTecnicos);
        List<Cliente> clientes = gerador.clientes(qtdClientes, agora);
        List<Chamado> chamados = gerador.chamados(qtdChamados, meses, clientes, tecnicos, agora);

        mongo.insert(tecnicos, Tecnico.class);
        inserirEmLotes(clientes, Cliente.class);
        inserirEmLotes(chamados, Chamado.class);
        garantirUsuariosBase();
        criarUsuariosTecnicos(tecnicos);

        log.info("Carga inicial concluida em {} ms: {} clientes, {} tecnicos, {} chamados, {} usuarios",
                System.currentTimeMillis() - t0, clientes.size(), tecnicos.size(), chamados.size(), usuarios.count());
    }

    private <T> void inserirEmLotes(List<T> itens, Class<T> tipo) {
        for (int i = 0; i < itens.size(); i += LOTE) {
            mongo.insert(itens.subList(i, Math.min(i + LOTE, itens.size())), tipo);
        }
    }

    /** Usuarios de demonstracao (senhas fracas apenas para ambiente academico). */
    private void garantirUsuariosBase() {
        criarSeNaoExiste("admin", "Administrador", senhaAdmin, "ROLE_ADMIN");
        criarSeNaoExiste("gestor", "Gestor de Suporte", "Gestor@123", "ROLE_MANAGER");
    }

    private void criarUsuariosTecnicos(List<Tecnico> tecnicos) {
        for (Tecnico t : tecnicos) {
            String login = t.getEmail().substring(0, t.getEmail().indexOf('@'));
            criarSeNaoExiste(login, t.getNome(), "Tecnico@123", "ROLE_USER");
        }
    }

    private void criarSeNaoExiste(String username, String nome, String senha, String role) {
        if (usuarios.findByUsername(username) != null) {
            return;
        }
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setNome(nome);
        u.setPassword(encoder.encode(senha));
        u.setRole(role);
        usuarios.save(u);
    }
}
