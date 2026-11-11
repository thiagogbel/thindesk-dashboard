package com.pfc.thindesk.controller.web;

import com.pfc.thindesk.entity.Usuario;
import com.pfc.thindesk.repository.UsuarioRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login e cadastro (base: atividade Login Seguro).
 * Correcao de seguranca: o cadastro publico cria SEMPRE perfil ROLE_USER;
 * perfis ADMIN/MANAGER nao podem ser escolhidos por quem se cadastra.
 */
@Controller
public class AuthController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public AuthController(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @GetMapping("/")
    public String inicio(Authentication auth) {
        boolean gestor = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
        return gestor ? "redirect:/dashboard" : "redirect:/chamados";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/cadastro")
    public String cadastro() {
        return "cadastro";
    }

    @PostMapping("/cadastro")
    public String salvar(@RequestParam String username, @RequestParam String password,
                         @RequestParam(required = false) String nome) {
        String login = username == null ? "" : username.trim().toLowerCase();
        if (!login.matches("[a-z0-9._-]{3,30}")) {
            return "redirect:/cadastro?invalido=true";
        }
        if (password == null || password.length() < 8) {
            return "redirect:/cadastro?senhaCurta=true";
        }
        if (usuarios.findByUsername(login) != null) {
            return "redirect:/cadastro?existe=true";
        }
        Usuario u = new Usuario();
        u.setUsername(login);
        u.setNome(nome == null || nome.isBlank() ? login : nome.trim());
        u.setPassword(encoder.encode(password));
        u.setRole("ROLE_USER");
        usuarios.save(u);
        return "redirect:/login?cadastrado=true";
    }
}
