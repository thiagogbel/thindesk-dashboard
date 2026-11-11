package com.pfc.thindesk.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Cliente / colaborador que abre chamados (colecao "clientes"). */
@Document(collection = "clientes")
public class Cliente {

    @Id
    private String id;
    @Indexed
    private String nome;
    @Indexed(unique = true)
    private String email;
    private String telefone;
    @Indexed
    private String setor;
    private String unidade;
    private Instant dataCadastro;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getSetor() { return setor; }
    public void setSetor(String setor) { this.setor = setor; }

    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }

    public Instant getDataCadastro() { return dataCadastro; }
    public void setDataCadastro(Instant dataCadastro) { this.dataCadastro = dataCadastro; }
}
