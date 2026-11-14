#!/usr/bin/env bash
# Levanta Jenkins en Docker (macOS / Linux).
#   Uso: scripts/setup-jenkins.sh [--tunnel]
set -euo pipefail
cd "$(dirname "$0")/../jenkins"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Se creó jenkins/.env a partir de .env.example."
  echo "Editalo (REPO_URL, contraseñas, NGROK_AUTHTOKEN) y volvé a ejecutar este script."
  exit 1
fi

PROFILE=()
if [ "${1:-}" = "--tunnel" ]; then PROFILE=(--profile tunnel); fi

docker compose "${PROFILE[@]}" up -d --build
echo "Esperando a Jenkins..."
until curl -fs -o /dev/null http://localhost:8080/login; do sleep 3; done
echo "Jenkins listo en http://localhost:8080 (usuario definido en jenkins/.env)"

if [ "${1:-}" = "--tunnel" ]; then
  sleep 3
  URL="$(curl -fs http://localhost:4040/api/tunnels | sed -nE 's/.*"public_url":"(https:[^"]+)".*/\1/p')"
  echo "URL pública (ngrok): ${URL:-ver http://localhost:4040}"
  echo "Webhook de GitHub:   ${URL}/github-webhook/   (Content type: application/json, evento: push)"
fi
