package com.pfc.thindesk.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Usuario do sistema (login). Reaproveitado da atividade de Login Seguro:
 * senha com hash BCrypt e perfil de acesso (ROLE_ADMIN, ROLE_MANAGER, ROLE_USER).
 */
@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;
    @Indexed(unique = true)
    private String username;
    private String password;
    private String nome;
    private String role;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
