# Rollback manual desde Windows: ejecuta rollback.sh dentro del contenedor de Jenkins
#   Uso: .\scripts\rollback.ps1 prod
param([Parameter(Mandatory)][ValidateSet("uat","prod")][string]$Environment)
docker exec rps-jenkins bash -c "cd /var/jenkins_home/workspace/piedra-papel-tijera-3d && scripts/rollback.sh $Environment"
