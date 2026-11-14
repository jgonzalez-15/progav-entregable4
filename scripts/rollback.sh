#!/usr/bin/env bash
# Vuelve un ambiente a la versión anterior guardada por deploy.sh.
#   Uso: scripts/rollback.sh <uat|prod> [repositorio-imagen]
set -euo pipefail
ENVIRONMENT="${1:?Uso: rollback.sh <uat|prod>}"
REPO="${2:-rps3d}"
PREVIOUS="${REPO}:${ENVIRONMENT}-previous"

if ! docker image inspect "$PREVIOUS" >/dev/null 2>&1; then
  echo "No hay versión anterior (${PREVIOUS}) para hacer rollback" >&2
  exit 1
fi
# Se usa un tag temporal para que deploy.sh no pise el respaldo con la versión fallida
docker tag "$PREVIOUS" "${REPO}:${ENVIRONMENT}-rollback"
"$(dirname "$0")/deploy.sh" "$ENVIRONMENT" "${REPO}:${ENVIRONMENT}-rollback"
echo "Rollback de ${ENVIRONMENT} completado"
