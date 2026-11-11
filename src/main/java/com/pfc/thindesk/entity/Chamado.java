package com.pfc.thindesk.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Chamado tecnico (colecao "chamados").
 *
 * Mantem os campos do Thindesk original (descricao, status, tipo, tecnico,
 * usuario) e acrescenta os dados necessarios para o dashboard: datas do
 * ciclo de vida, prioridade/SLA, setor e consideracoes tecnicas.
 *
 * tempoResolucaoMin e slaCumprido sao calculados no fechamento
 * (desnormalizacao proposital para deixar as agregacoes do dashboard leves).
 */
@Document(collection = "chamados")
@CompoundIndexes({
    @CompoundIndex(name = "status_abertura", def = "{'status': 1, 'dataAbertura': -1}"),
    @CompoundIndex(name = "tipo_abertura", def = "{'tipo': 1, 'dataAbertura': -1}")
})
public class Chamado {

    @Id
    private String id;

    @Indexed(unique = true)
    private String protocolo;

    private String titulo;
    private String descricao;

    @Indexed
    private StatusChamado status;

    private String tipo;
    private Prioridade prioridade;
    private String canal;

    /* tecnico responsavel (nome mantido para compatibilidade com o Thindesk) */
    private String tecnicoId;
    @Indexed
    private String tecnico;

    /* cliente solicitante (campo "usuario" do Thindesk original) */
    private String clienteId;
    private String usuario;
    private String setor;

    @Indexed
    private Instant dataAbertura;
    private Instant dataPrimeiroAtendimento;
    @Indexed
    private Instant dataFechamento;
    private Instant prazoSla;
    private Instant ultimaAtualizacao;

    private Boolean slaCumprido;
    private Long tempoResolucaoMin;
    private Integer avaliacao;
    private String consideracaoTecnica;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProtocolo() { return protocolo; }
    public void setProtocolo(String protocolo) { this.protocolo = protocolo; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public StatusChamado getStatus() { return status; }
    public void setStatus(StatusChamado status) { this.status = status; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Prioridade getPrioridade() { return prioridade; }
    public void setPrioridade(Prioridade prioridade) { this.prioridade = prioridade; }

    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }

    public String getTecnicoId() { return tecnicoId; }
    public void setTecnicoId(String tecnicoId) { this.tecnicoId = tecnicoId; }

    public String getTecnico() { return tecnico; }
    public void setTecnico(String tecnico) { this.tecnico = tecnico; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getSetor() { return setor; }
    public void setSetor(String setor) { this.setor = setor; }

    public Instant getDataAbertura() { return dataAbertura; }
    public void setDataAbertura(Instant dataAbertura) { this.dataAbertura = dataAbertura; }

    public Instant getDataPrimeiroAtendimento() { return dataPrimeiroAtendimento; }
    public void setDataPrimeiroAtendimento(Instant dataPrimeiroAtendimento) { this.dataPrimeiroAtendimento = dataPrimeiroAtendimento; }

    public Instant getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(Instant dataFechamento) { this.dataFechamento = dataFechamento; }

    public Instant getPrazoSla() { return prazoSla; }
    public void setPrazoSla(Instant prazoSla) { this.prazoSla = prazoSla; }

    public Instant getUltimaAtualizacao() { return ultimaAtualizacao; }
    public void setUltimaAtualizacao(Instant ultimaAtualizacao) { this.ultimaAtualizacao = ultimaAtualizacao; }

    public Boolean getSlaCumprido() { return slaCumprido; }
    public void setSlaCumprido(Boolean slaCumprido) { this.slaCumprido = slaCumprido; }

    public Long getTempoResolucaoMin() { return tempoResolucaoMin; }
    public void setTempoResolucaoMin(Long tempoResolucaoMin) { this.tempoResolucaoMin = tempoResolucaoMin; }

    public Integer getAvaliacao() { return avaliacao; }
    public void setAvaliacao(Integer avaliacao) { this.avaliacao = avaliacao; }

    public String getConsideracaoTecnica() { return consideracaoTecnica; }
    public void setConsideracaoTecnica(String consideracaoTecnica) { this.consideracaoTecnica = consideracaoTecnica; }
}
