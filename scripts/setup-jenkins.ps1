# Levanta Jenkins en Docker (Windows / PowerShell).
#   Uso: .\scripts\setup-jenkins.ps1 [-Tunnel]
param([switch]$Tunnel)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..\jenkins")

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Se creó jenkins\.env a partir de .env.example."
    Write-Host "Editalo (REPO_URL, contraseñas, NGROK_AUTHTOKEN) y volvé a ejecutar este script."
    exit 1
}

if ($Tunnel) {
    docker compose --profile tunnel up -d --build
} else {
    docker compose up -d --build
}
if ($LASTEXITCODE -ne 0) { throw "docker compose falló" }

Write-Host "Esperando a Jenkins..."
do {
    Start-Sleep -Seconds 3
    try { $ok = (Invoke-WebRequest -UseBasicParsing http://localhost:8080/login).StatusCode -eq 200 } catch { $ok = $false }
} until ($ok)
Write-Host "Jenkins listo en http://localhost:8080 (usuario definido en jenkins\.env)"

if ($Tunnel) {
    Start-Sleep -Seconds 3
    $tunnels = Invoke-RestMethod http://localhost:4040/api/tunnels
    $url = ($tunnels.tunnels | Where-Object { $_.public_url -like "https*" } | Select-Object -First 1).public_url
    Write-Host "URL pública (ngrok): $url"
    Write-Host "Webhook de GitHub:   $url/github-webhook/   (Content type: application/json, evento: push)"
}
