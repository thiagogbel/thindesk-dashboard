package com.pfc.thindesk.repository;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.StatusChamado;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class MongoDashboardConsultas implements DashboardConsultas {

    private static final String COLECAO = "chamados";

    private final MongoTemplate mongo;

    public MongoDashboardConsultas(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /* ------------------------------------------------------------------ */
    /* Montagem do filtro ($match)                                         */
    /* ------------------------------------------------------------------ */

    Criteria criterio(FiltroDashboard f, String campoData, boolean somenteAtendidos) {
        List<Criteria> partes = new ArrayList<>();
        partes.add(Criteria.where(campoData).gte(Date.from(f.inicioUtc())).lt(Date.from(f.fimUtc())));

        if (somenteAtendidos) {
            List<String> atendidos = List.of(StatusChamado.RESOLVIDO.name(), StatusChamado.FECHADO.name());
            List<String> aplicados = f.status().isEmpty()
                    ? atendidos
                    : f.status().stream().map(Enum::name).filter(atendidos::contains).toList();
            partes.add(Criteria.where("status").in(aplicados));
        } else if (!f.status().isEmpty()) {
            partes.add(Criteria.where("status").in(f.status().stream().map(Enum::name).toList()));
        }
        if (f.tipo() != null) {
            partes.add(Criteria.where("tipo").is(f.tipo()));
        }
        if (f.prioridade() != null) {
            partes.add(Criteria.where("prioridade").is(f.prioridade().name()));
        }
        if (f.tecnico() != null) {
            partes.add(Criteria.where("tecnico").is(f.tecnico()));
        }
        if (f.setor() != null) {
            partes.add(Criteria.where("setor").is(f.setor()));
        }
        return new Criteria().andOperator(partes.toArray(new Criteria[0]));
    }

    /* ------------------------------------------------------------------ */

    @Override
    public long contar(FiltroDashboard filtro, String campoData, boolean somenteAtendidos) {
        return mongo.count(new Query(criterio(filtro, campoData, somenteAtendidos)), COLECAO);
    }

    @Override
    public long contarAtrasados(FiltroDashboard filtro, Instant agora) {
        List<String> emAberto = StatusChamado.emAberto().stream().map(Enum::name).toList();
        Criteria c = new Criteria().andOperator(
                criterio(filtro, DATA_ABERTURA, false),
                Criteria.where("status").in(emAberto),
                Criteria.where("prazoSla").lt(Date.from(agora)));
        return mongo.count(new Query(c), COLECAO);
    }

    @Override
    public Map<String, Long> contarPor(String campo, FiltroDashboard filtro, String campoData, boolean somenteAtendidos) {
        Aggregation agg = newAggregation(
                match(criterio(filtro, campoData, somenteAtendidos)),
                group(campo).count().as("total"),
                sort(Sort.Direction.DESC, "total"));
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(agg, COLECAO, Document.class).getMappedResults()) {
            Object chave = d.get("_id");
            resultado.put(chave == null ? null : chave.toString(), numero(d.get("total")));
        }
        return resultado;
    }

    @Override
    public Map<String, Long> contarPorPeriodo(String formatoMongo, FiltroDashboard filtro, String campoData,
                                              boolean somenteAtendidos, String fuso) {
        Aggregation agg = newAggregation(
                match(criterio(filtro, campoData, somenteAtendidos)),
                project().and(DateOperators.DateToString.dateOf(campoData)
                                .toString(formatoMongo)
                                .withTimezone(DateOperators.Timezone.valueOf(fuso)))
                        .as("periodo"),
                group("periodo").count().as("total"),
                sort(Sort.Direction.ASC, "_id"));
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (Document d : mongo.aggregate(agg, COLECAO, Document.class).getMappedResults()) {
            resultado.put(String.valueOf(d.get("_id")), numero(d.get("total")));
        }
        return resultado;
    }

    @Override
    public List<Estatistica> estatisticasPor(String campo, FiltroDashboard filtro, String campoData,
                                             boolean somenteAtendidos, int limite) {
        Aggregation agg = newAggregation(
                match(criterio(filtro, campoData, somenteAtendidos)),
                group(campo).count().as("total")
                        .sum(se(statusAtendido())).as("atendidos")
                        .avg("tempoResolucaoMin").as("tempoMedioMin")
                        .avg("avaliacao").as("avaliacaoMedia")
                        .sum(se(ComparisonOperators.valueOf("slaCumprido").equalToValue(true))).as("dentroSla")
                        .sum(se(ComparisonOperators.valueOf("slaCumprido").equalToValue(false))).as("foraSla"),
                sort(Sort.Direction.DESC, "total"),
                limit(limite));
        return mongo.aggregate(agg, COLECAO, Document.class).getMappedResults().stream()
                .map(this::paraEstatistica)
                .toList();
    }

    @Override
    public Estatistica estatisticasGerais(FiltroDashboard filtro, String campoData, boolean somenteAtendidos) {
        Aggregation agg = newAggregation(
                match(criterio(filtro, campoData, somenteAtendidos)),
                group().count().as("total")
                        .sum(se(statusAtendido())).as("atendidos")
                        .avg("tempoResolucaoMin").as("tempoMedioMin")
                        .avg("avaliacao").as("avaliacaoMedia")
                        .sum(se(ComparisonOperators.valueOf("slaCumprido").equalToValue(true))).as("dentroSla")
                        .sum(se(ComparisonOperators.valueOf("slaCumprido").equalToValue(false))).as("foraSla"));
        List<Document> docs = mongo.aggregate(agg, COLECAO, Document.class).getMappedResults();
        if (docs.isEmpty()) {
            return new Estatistica(null, 0, 0, null, null, 0, 0);
        }
        return paraEstatistica(docs.get(0));
    }

    @Override
    public List<String> valoresDistintos(String campo) {
        return mongo.findDistinct(new Query(), campo, COLECAO, String.class).stream()
                .filter(v -> v != null && !v.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public LocalDate dataMinima(String fuso) {
        return dataExtrema(Sort.Direction.ASC, fuso);
    }

    @Override
    public LocalDate dataMaxima(String fuso) {
        return dataExtrema(Sort.Direction.DESC, fuso);
    }

    /* ------------------------------------------------------------------ */

    private LocalDate dataExtrema(Sort.Direction direcao, String fuso) {
        Query q = new Query(Criteria.where(DATA_ABERTURA).ne(null)).with(Sort.by(direcao, DATA_ABERTURA)).limit(1);
        q.fields().include(DATA_ABERTURA);
        Chamado c = mongo.findOne(q, Chamado.class);
        return c == null || c.getDataAbertura() == null ? null : c.getDataAbertura().atZone(ZoneId.of(fuso)).toLocalDate();
    }

    private static AggregationExpression statusAtendido() {
        return ConditionalOperators.when(
                        ComparisonOperators.valueOf("status").equalToValue(StatusChamado.RESOLVIDO.name()))
                .then(true)
                .otherwiseValueOf(ComparisonOperators.valueOf("status").equalToValue(StatusChamado.FECHADO.name()));
    }

    /** $cond: soma 1 quando a expressao e verdadeira. */
    private static AggregationExpression se(AggregationExpression condicao) {
        return ConditionalOperators.when(condicao).then(1).otherwise(0);
    }

    private Estatistica paraEstatistica(Document d) {
        Object chave = d.get("_id");
        return new Estatistica(
                chave == null ? null : chave.toString(),
                numero(d.get("total")),
                numero(d.get("atendidos")),
                decimal(d.get("tempoMedioMin")),
                decimal(d.get("avaliacaoMedia")),
                numero(d.get("dentroSla")),
                numero(d.get("foraSla")));
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static Double decimal(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
