#!/usr/bin/env bash
# Smoke test non distruttivo dello stack MIDA già avviato con Docker Compose.
# Non crea, importa o cancella file DidUP: verifica solo frontend, proxy e API.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8200}"
CURL=(curl -fsS)
if [[ "${INSECURE:-0}" == "1" ]]; then
  CURL+=(-k)
fi

fail() {
  echo "ERRORE: $*" >&2
  exit 1
}

echo "MIDA smoke test su ${BASE_URL}"

"${CURL[@]}" "${BASE_URL}/" | grep -q "MIDA" || fail "il frontend non risponde o non contiene MIDA"
echo "  ok  frontend"

"${CURL[@]}" "${BASE_URL}/manifest.webmanifest" | grep -q '"name": "MIDA' || fail "manifest PWA non valido"
echo "  ok  manifest PWA"

"${CURL[@]}" "${BASE_URL}/ngsw-worker.js" | grep -q "MIDA_PUSH_HANDLERS" || fail "handler Web Push assenti dal service worker"
echo "  ok  service worker con Web Push"

"${CURL[@]}" "${BASE_URL}/api/imports/status" >/dev/null || fail "API /api/imports/status non raggiungibile"
echo "  ok  API attraverso nginx"

"${CURL[@]}" "${BASE_URL}/actuator/health" >/dev/null 2>&1 && fail "Actuator non deve essere esposto dal frontend"
echo "  ok  actuator non esposto"

"${CURL[@]}" "${BASE_URL}/v3/api-docs" | grep -q '"title":"MIDA API"' || fail "OpenAPI non disponibile"
echo "  ok  OpenAPI"

for service in mida-mongodb mida-backend mida-frontend mida-proxy; do
  state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${service}" 2>/dev/null || true)"
  [[ "${state}" == "healthy" || "${state}" == "running" ]] || fail "${service} non è in esecuzione (${state:-assente})"
  echo "  ok  ${service}: ${state}"
done

if [[ -n "$(docker port mida-mongodb 2>/dev/null)" ]]; then
  fail "mida-mongodb pubblica porte sull'host: $(docker port mida-mongodb)"
fi
echo "  ok  MongoDB MIDA non pubblicato sull'host"

echo "Smoke test completato."
