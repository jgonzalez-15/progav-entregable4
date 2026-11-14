#!/usr/bin/env bash
# Pruebas de humo contra un ambiente desplegado.
#   Uso: scripts/smoke-test.sh <url-base> <ENV esperado> <versión esperada>
# Verifica: health UP, ambiente/versión correctos, frontend servido y una ronda PvE real.
set -euo pipefail

BASE_URL="${1:?Uso: smoke-test.sh <url-base> <ENV> <versión>}"
EXPECTED_ENV="${2:?Falta el ambiente esperado}"
EXPECTED_VERSION="${3:?Falta la versión esperada}"
TIMEOUT="${SMOKE_TIMEOUT:-120}"

echo "Esperando a que ${BASE_URL} esté saludable (máx ${TIMEOUT}s)..."
deadline=$((SECONDS + TIMEOUT))
until curl -fsS "${BASE_URL}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "ERROR: la aplicación no respondió UP a tiempo" >&2
    exit 1
  fi
  sleep 3
done
echo "OK health UP"

INFO="$(curl -fsS "${BASE_URL}/api/info")"
echo "Info: ${INFO}"
echo "$INFO" | grep -q "\"env\":\"${EXPECTED_ENV}\"" || { echo "ERROR: ambiente incorrecto (esperado ${EXPECTED_ENV})" >&2; exit 1; }
echo "$INFO" | grep -q "\"version\":\"${EXPECTED_VERSION}\"" || { echo "ERROR: versión incorrecta (esperada ${EXPECTED_VERSION})" >&2; exit 1; }
echo "OK ambiente ${EXPECTED_ENV} versión ${EXPECTED_VERSION}"

curl -fsS "${BASE_URL}/" | grep -q "Piedra, Papel o Tijera" || { echo "ERROR: no se sirve el frontend" >&2; exit 1; }
echo "OK frontend"

SESSION="$(curl -fsS -X POST -H 'Content-Type: application/json' -d '{"name":"SmokeTest"}' "${BASE_URL}/api/pve/sessions")"
SESSION_ID="$(echo "$SESSION" | sed -E 's/.*"sessionId":"([^"]+)".*/\1/')"
ROUND="$(curl -fsS -X POST -H 'Content-Type: application/json' -d '{"move":"ROCK"}' "${BASE_URL}/api/pve/sessions/${SESSION_ID}/rounds")"
echo "$ROUND" | grep -Eq '"outcome":"(WIN|LOSE|DRAW)"' || { echo "ERROR: ronda PvE inválida: ${ROUND}" >&2; exit 1; }
curl -fsS -X DELETE "${BASE_URL}/api/pve/sessions/${SESSION_ID}" >/dev/null
echo "OK ronda PvE: ${ROUND}"
LB="$(curl -fsS "${BASE_URL}/api/leaderboard")"
echo "$LB" | grep -q '"top":\[' || { echo "ERROR: /api/leaderboard inválido" >&2; exit 1; }
echo "OK ranking competitivo (totalRanked: $(echo "$LB" | sed -E 's/.*"totalRanked":([0-9]+).*/\1/'))"
echo "Smoke test ${EXPECTED_ENV} superado"
