package com.pfc.thindesk.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste de integracao ponta a ponta: sobe a aplicacao inteira, conecta no
 * MongoDB real, gera uma base menor e chama a API via HTTP.
 *
 * So roda quando a variavel MONGODB_URI_TESTE esta definida (use um banco
 * separado, ex.: .../thindesk_teste). Comando:
 *   ./mvnw test -Dtest=ApiIntegracaoIT
 */
@EnabledIfEnvironmentVariable(named = "MONGODB_URI_TESTE", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.mongodb.uri=${MONGODB_URI_TESTE}",
        "thindesk.seed.reset=true",
        "thindesk.seed.clientes=200",
        "thindesk.seed.tecnicos=8",
        "thindesk.seed.chamados=3000",
        "thindesk.seed.meses=6"
})
class ApiIntegracaoIT {

    @Value("${local.server.port}")
    int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> chamar(String metodo, String caminho, String usuario, String senha, String corpo) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Accept", "application/json");
        if (usuario != null) {
            String token = Base64.getEncoder().encodeToString((usuario + ":" + senha).getBytes(StandardCharsets.UTF_8));
            b.header("Authorization", "Basic " + token);
        }
        if (corpo != null) {
            b.header("Content-Type", "application/json").method(metodo, HttpRequest.BodyPublishers.ofString(corpo));
        } else {
            b.method(metodo, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long numero(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    @Test
    @DisplayName("Sem autenticacao a API responde 401 em JSON")
    void semLogin() throws Exception {
        HttpResponse<String> r = chamar("GET", "/api/v1/dashboard/resumo", null, null, null);
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.body()).contains("\"status\":401");
    }

    @Test
    @DisplayName("Tecnico (ROLE_USER) nao acessa o dashboard: 403")
    void perfilSemPermissao() throws Exception {
        // a carga cria um usuario ROLE_USER por tecnico (login = parte do e-mail antes do @)
        HttpResponse<String> lista = chamar("GET", "/api/v1/tecnicos", "admin", "Admin@123", null);
        assertThat(lista.statusCode()).isEqualTo(200);
        Matcher m = Pattern.compile("\"email\":\"([^@\"]+)@").matcher(lista.body());
        assertThat(m.find()).isTrue();
        HttpResponse<String> r = chamar("GET", "/api/v1/dashboard/resumo", m.group(1), "Tecnico@123", null);
        assertThat(r.statusCode()).as("usuario " + m.group(1)).isEqualTo(403);
    }

    @Test
    @DisplayName("Indicadores sao consistentes com a distribuicao por status")
    void resumoConsistente() throws Exception {
        String q = "?inicio=2000-01-01&fim=2001-12-31";
        HttpResponse<String> vazio = chamar("GET", "/api/v1/dashboard/resumo" + q, "admin", "Admin@123", null);
        assertThat(vazio.statusCode()).isEqualTo(200);
        assertThat(numero(vazio.body(), "totalAbertosNoPeriodo")).isZero();

        HttpResponse<String> resumo = chamar("GET", "/api/v1/dashboard/resumo", "gestor", "Gestor@123", null);
        assertThat(resumo.statusCode()).isEqualTo(200);
        long total = numero(resumo.body(), "totalAbertosNoPeriodo");
        assertThat(total).isPositive();

        HttpResponse<String> status = chamar("GET", "/api/v1/dashboard/status", "gestor", "Gestor@123", null);
        long soma = 0;
        Matcher m = Pattern.compile("\"total\"\\s*:\\s*(\\d+)").matcher(status.body());
        while (m.find()) {
            soma += Long.parseLong(m.group(1));
        }
        assertThat(soma).isEqualTo(total);
    }

    @Test
    @DisplayName("Erros de parametro chegam como 400 em JSON")
    void erros() throws Exception {
        assertThat(chamar("GET", "/api/v1/dashboard/resumo?inicio=2026-12-01&fim=2026-01-01", "admin", "Admin@123", null).statusCode()).isEqualTo(400);
        assertThat(chamar("GET", "/api/v1/dashboard/chamados-por-periodo?granularidade=ANO", "admin", "Admin@123", null).statusCode()).isEqualTo(400);
        assertThat(chamar("GET", "/api/v1/dashboard/distribuicao/cor", "admin", "Admin@123", null).statusCode()).isEqualTo(400);
        assertThat(chamar("GET", "/api/v1/chamados/000000000000000000000000", "admin", "Admin@123", null).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Atualizar status de um chamado aberto altera o backlog (RF07)")
    void atualizacaoRefleteNoDashboard() throws Exception {
        HttpResponse<String> abertos = chamar("GET", "/api/v1/chamados?status=ABERTO&tamanho=1", "admin", "Admin@123", null);
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f]{24})\"").matcher(abertos.body());
        if (!m.find()) {
            return; // base sem chamados ABERTO no momento
        }
        String id = m.group(1);
        String periodo = "?inicio=" + java.time.LocalDate.now().minusDays(700);
        long antes = numero(chamar("GET", "/api/v1/dashboard/resumo" + periodo, "admin", "Admin@123", null).body(), "abertos");

        HttpResponse<String> r = chamar("PATCH", "/api/v1/chamados/" + id + "/status", "admin", "Admin@123",
                "{\"status\":\"EM_ANDAMENTO\",\"consideracaoTecnica\":\"Teste de integração\"}");
        assertThat(r.statusCode()).isEqualTo(200);

        long depois = numero(chamar("GET", "/api/v1/dashboard/resumo" + periodo, "admin", "Admin@123", null).body(), "abertos");
        assertThat(depois).isEqualTo(antes - 1);

        HttpResponse<String> invalida = chamar("PATCH", "/api/v1/chamados/" + id + "/status", "admin", "Admin@123", "{\"status\":\"ABERTO\"}");
        assertThat(invalida.statusCode()).isEqualTo(422);
    }
}
