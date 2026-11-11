package com.pfc.thindesk.seed;

import com.pfc.thindesk.entity.StatusChamado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Tratamento de dados (RF07): o Thindesk original gravava o status como texto
 * livre ("Aberto", "Em andamento"...) e o tecnico vazio. Ao iniciar, os
 * registros antigos sao convertidos para o padrao atual, para que as
 * agregacoes do dashboard nao gerem categorias duplicadas.
 */
@Component
@Order(1)
public class NormalizadorDadosLegados implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(NormalizadorDadosLegados.class);

    private final MongoTemplate mongo;

    public NormalizadorDadosLegados(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(String... args) {
        long alterados = 0;
        for (StatusChamado s : StatusChamado.values()) {
            for (String variante : new String[] {s.getLabel(), s.getLabel().toLowerCase(), s.getLabel().toUpperCase(),
                    s.name().toLowerCase(), s.name().replace('_', ' ')}) {
                if (variante.equals(s.name())) {
                    continue;
                }
                alterados += mongo.updateMulti(new Query(Criteria.where("status").is(variante)),
                        Update.update("status", s.name()), "chamados").getModifiedCount();
            }
        }
        // "Tipo" com T maiusculo no JSON de exemplo do Thindesk original
        alterados += mongo.updateMulti(new Query(Criteria.where("Tipo").exists(true).and("tipo").exists(false)),
                new Update().rename("Tipo", "tipo"), "chamados").getModifiedCount();
        alterados += mongo.updateMulti(new Query(Criteria.where("tecnico").is("")),
                new Update().unset("tecnico"), "chamados").getModifiedCount();
        if (alterados > 0) {
            log.info("Normalizacao de dados legados: {} documento(s) ajustado(s)", alterados);
        }
    }
}
