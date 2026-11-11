#!/usr/bin/env bash
# Executa as chamadas da API e salva as respostas em docs/evidencias/api
# Uso: ./scripts/testar-api.sh [url-base]   (padrao http://localhost:8080)
set -u
BASE="${1:-http://localhost:8080}"
OUT="docs/evidencias/api"
ADMIN="admin:${THINDESK_ADMIN_PASSWORD:-Admin@123}"
mkdir -p "$OUT"
RESUMO="$OUT/00-resumo-dos-testes.txt"
: > "$RESUMO"

caso() { # nome esperado metodo caminho [usuario] [corpo]
  local nome="$1" esperado="$2" metodo="$3" caminho="$4" usuario="${5:-$ADMIN}" corpo="${6:-}"
  local args=(-s -o "$OUT/$nome.json" -w "%{http_code}" -X "$metodo" -H "Accept: application/json")
  [ "$usuario" != "-" ] && args+=(-u "$usuario")
  [ -n "$corpo" ] && args+=(-H "Content-Type: application/json" -d "$corpo")
  local codigo; codigo=$(curl "${args[@]}" "$BASE$caminho")
  local res="OK"; [ "$codigo" != "$esperado" ] && res="FALHOU"
  printf '%-6s %-40s esperado=%s obtido=%s  %s %s\n' "$res" "$nome" "$esperado" "$codigo" "$metodo" "$caminho" | tee -a "$RESUMO"
}

caso 01-resumo                    200 GET  "/api/v1/dashboard/resumo"
caso 02-resumo-filtrado           200 GET  "/api/v1/dashboard/resumo?inicio=2026-01-01&fim=2026-06-30&prioridade=ALTA"
caso 03-serie-mensal              200 GET  "/api/v1/dashboard/chamados-por-periodo?granularidade=MES&inicio=2025-12-01&fim=2026-10-31"
caso 04-serie-auto                200 GET  "/api/v1/dashboard/chamados-por-periodo"
caso 05-status                    200 GET  "/api/v1/dashboard/status"
caso 06-tipos                     200 GET  "/api/v1/dashboard/tipos?limite=5"
caso 07-tecnicos                  200 GET  "/api/v1/dashboard/tecnicos?limite=5"
caso 08-distribuicao-setor        200 GET  "/api/v1/dashboard/distribuicao/setor"
caso 09-filtros                   200 GET  "/api/v1/dashboard/filtros"
caso 10-chamados-paginados        200 GET  "/api/v1/chamados?status=ABERTO,EM_ANDAMENTO&tamanho=5"
caso 11-erro-sem-login            401 GET  "/api/v1/dashboard/resumo" "-"
caso 12-erro-perfil-sem-acesso    403 GET  "/api/v1/dashboard/resumo" "$(curl -s -u "$ADMIN" "$BASE/api/v1/tecnicos" | grep -o '"email":"[^@"]*' | head -1 | cut -d'"' -f4):Tecnico@123"
caso 13-erro-datas-invertidas     400 GET  "/api/v1/dashboard/resumo?inicio=2026-12-01&fim=2026-01-01"
caso 14-erro-data-invalida        400 GET  "/api/v1/dashboard/resumo?inicio=31-12-2026"
caso 15-erro-granularidade        400 GET  "/api/v1/dashboard/chamados-por-periodo?granularidade=ANO"
caso 16-erro-dimensao             400 GET  "/api/v1/dashboard/distribuicao/cor"
caso 17-erro-limite               400 GET  "/api/v1/dashboard/tipos?limite=500"
caso 18-erro-chamado-inexistente  404 GET  "/api/v1/chamados/000000000000000000000000"
caso 19-erro-corpo-invalido       400 POST "/api/v1/chamados" "$ADMIN" '{"titulo":""}'

CLIENTE=$(curl -s -u "$ADMIN" "$BASE/api/v1/clientes?tamanho=1" | grep -o '"id":"[0-9a-f]*"' | head -1 | cut -d'"' -f4)
caso 20-criar-chamado             201 POST "/api/v1/chamados" "$ADMIN" "{\"titulo\":\"Teste de evidencia\",\"tipo\":\"Hardware\",\"prioridade\":\"ALTA\",\"clienteId\":\"$CLIENTE\"}"
NOVO=$(grep -o '"id":"[0-9a-f]*"' "$OUT/20-criar-chamado.json" | head -1 | cut -d'"' -f4)
caso 21-iniciar-atendimento       200 PATCH "/api/v1/chamados/$NOVO/status" "$ADMIN" '{"status":"EM_ANDAMENTO"}'
caso 22-erro-resolver-sem-nota    422 PATCH "/api/v1/chamados/$NOVO/status" "$ADMIN" '{"status":"RESOLVIDO"}'
caso 23-resolver                  200 PATCH "/api/v1/chamados/$NOVO/status" "$ADMIN" '{"status":"RESOLVIDO","consideracaoTecnica":"Fonte substituida"}'
caso 24-fechar-com-avaliacao      200 PATCH "/api/v1/chamados/$NOVO/status" "$ADMIN" '{"status":"FECHADO","avaliacao":5}'
caso 25-erro-transicao-invalida   422 PATCH "/api/v1/chamados/$NOVO/status" "$ADMIN" '{"status":"ABERTO"}'

echo
echo "Respostas salvas em $OUT. Falhas: $(grep -c '^FALHOU' "$RESUMO")"
