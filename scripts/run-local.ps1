# Ambiente de DESARROLLO: corre la app localmente en http://localhost:8090
Set-Location (Join-Path $PSScriptRoot "..")
.\mvnw.cmd -B -ntp install -DskipTests
if ($LASTEXITCODE -eq 0) { .\mvnw.cmd -B -ntp -pl web spring-boot:run }
