#!/usr/bin/env bash
# Despliega una imagen en el ambiente indicado (contenedor Docker = "ambiente simulado").
#   Uso: scripts/deploy.sh <uat|prod> <imagen:tag>
# Antes de reemplazar el contenedor, guarda la imagen actual como <env>-previous
# para poder hacer rollback (scripts/rollback.sh).
set -euo pipefail

ENVIRONMENT="${1:?Uso: deploy.sh <uat|prod> <imagen:tag>}"
IMAGE="${2:?Falta la imagen a desplegar}"
NETWORK="${RPS_NETWORK:-rps-net}"

case "$ENVIRONMENT" in
  uat)  PORT="${UAT_PORT:-8081}";  APP_ENV=UAT;  DEMO_SEED=true ;;   # UAT con 150 jugadores de prueba
  prod) PORT="${PROD_PORT:-8082}"; APP_ENV=PROD; DEMO_SEED=false ;;
  *) echo "Ambiente desconocido: $ENVIRONMENT (usar uat o prod)" >&2; exit 2 ;;
esac

CONTAINER="rps-${ENVIRONMENT}"
REPO="${IMAGE%%:*}"

docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK"

# Respaldo de la versión en ejecución para rollback
if docker inspect "$CONTAINER" >/dev/null 2>&1; then
  CURRENT_IMAGE="$(docker inspect --format '{{.Config.Image}}' "$CONTAINER")"
  if [ "$CURRENT_IMAGE" != "$IMAGE" ]; then
    docker tag "$CURRENT_IMAGE" "${REPO}:${ENVIRONMENT}-previous"
    echo "Respaldo: ${CURRENT_IMAGE} -> ${REPO}:${ENVIRONMENT}-previous"
  fi
  docker rm -f "$CONTAINER" >/dev/null
fi

docker run -d \
  --name "$CONTAINER" \
  --network "$NETWORK" \
  --restart unless-stopped \
  -p "${PORT}:8090" \
  -e APP_ENV="$APP_ENV" \
  -e APP_DEMO_SEED="$DEMO_SEED" \
  -v "rps-data-${ENVIRONMENT}:/data" \
  --label "rps.env=${ENVIRONMENT}" \
  "$IMAGE" >/dev/null

docker tag "$IMAGE" "${REPO}:${ENVIRONMENT}-current"
echo "Desplegado ${IMAGE} en ${APP_ENV} -> contenedor ${CONTAINER}, http://localhost:${PORT}"
