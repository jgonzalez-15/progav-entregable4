# Piedra, Papel o Tijera 3D — Entregable 4 (CI/CD)

Programación Avanzada 2026 — Universidad de Montevideo.

Juego web de Piedra, Papel o Tijera con escena 3D (Three.js) ambientada en una oficina, modo
**contra la CPU** y **multijugador en tiempo real** (WebSockets, salas con código de 6 letras).
El foco del entregable es el **pipeline de Jenkins**: build, pruebas unitarias y de integración,
despliegue en **UAT**, **aprobación manual** y despliegue en **Producción**.

| Ambiente | Dónde corre | URL |
|---|---|---|
| DEV | máquina del desarrollador (`scripts/run-local.*`) | http://localhost:8090 |
| UAT | contenedor Docker `rps-uat` (lo crea Jenkins) | http://localhost:8081 |
| PROD | contenedor Docker `rps-prod` (lo crea Jenkins tras la aprobación) | http://localhost:8082 |
| Jenkins | contenedor `rps-jenkins` | http://localhost:8080 |

## Estructura

```
game-core/   Reglas, oponente aleatorio y marcador (Java puro)           + pruebas unitarias
rooms/       Salas PvP: códigos de 6 letras, nombres, colores únicos      + pruebas unitarias
web/         Spring Boot: REST (PvE), WebSocket (PvP), H2, frontend 3D    + unitarias e integración (*IT)
Jenkinsfile  Pipeline declarativo (pipeline as code)
Dockerfile   Imagen de la aplicación
jenkins/     Jenkins en Docker: Dockerfile, plugins, JCasC, docker-compose (+ ngrok)
scripts/     deploy / smoke-test / rollback (bash, los usa Jenkins) y setup/status/run-local (bash + PowerShell)
docs/        Guía de Jenkins y webhooks, guion de la demo, informe
```

## Requisitos

- Docker Desktop (Windows/macOS) o Docker Engine (Linux)
- Git
- JDK 21 solo para desarrollar localmente (Maven no hace falta: se usa `mvnw`)

## Desarrollo local

```bash
./mvnw clean verify          # compila + 97 pruebas unitarias + 22 de integración
./scripts/run-local.sh       # app en http://localhost:8090
```

En Windows (PowerShell):

```powershell
.\mvnw.cmd clean verify
.\scripts\run-local.ps1
```

Solo unitarias: `./mvnw test`. Solo integración: `./mvnw verify -DskipUTs=true`.

## Jenkins + GitHub

Guía completa con todos los comandos: [docs/JENKINS_SETUP.md](docs/JENKINS_SETUP.md).
Resumen:

```bash
cp jenkins/.env.example jenkins/.env      # completar REPO_URL, contraseñas y NGROK_AUTHTOKEN
./scripts/setup-jenkins.sh --tunnel        # Windows: .\scripts\setup-jenkins.ps1 -Tunnel
```

Después se configura el webhook de GitHub hacia `https://<url-ngrok>/github-webhook/`.

## Pipeline

```
Checkout → Build → Pruebas unitarias → Pruebas de integración → Imagen Docker
        → Deploy UAT (+ smoke test) → ⏸ Aprobación QA (manual en Jenkins) → Deploy Producción (+ smoke test, rollback automático)
```

- Disparo automático por **webhook** de GitHub (`githubPush`) y **polling** cada ~2 min como respaldo.
- Si fallan las pruebas, el pipeline se detiene y **nada se despliega**.
- En la etapa de aprobación QA elige **Aprobar** o **Rechazar** y deja notas. Un rechazo deja el build
  en `UNSTABLE`, archiva `uat-rechazo.txt` y **no toca producción**.
- La **misma imagen** probada en UAT es la que se promueve a producción.

## Cómo jugar

- **Contra la CPU**: ingresá tu nombre y elegí piedra, papel o tijera (botones o teclas 1/2/3).
- **Multijugador**: “Crear sala” genera un código de 6 letras. El rival lo ingresa en “Unirse”.
  Cada jugador recibe automáticamente un color de camiseta distinto.
- El marcador lleva las rondas ganadas y los empates. “Abandonar” (o `Esc`) vuelve al inicio en cualquier momento.
- **Competitivo (V2)**: “🏆 Competitivo” te empareja con alguien de victorias parecidas, al mejor de 3.
  Solo estas victorias cuentan para el **📊 Ranking**. En **🎉 Celebraciones** elegís tu festejo de victoria.

## Versión 2 (competitivo)

La rama `main` incluye la **V2**: modo competitivo con matchmaking por victorias, ranking global
antifraude (Top 100 + posición exacta) y celebraciones 3D configurables. La V1 entregada está en el tag `v1.0`.
Detalle de diseño, concurrencia y pruebas: [docs/V2.md](docs/V2.md).

```
matchmaking/  Cola de emparejamiento thread-safe por victorias (Java puro)   + pruebas unitarias y de estrés
```

## Demo de la defensa

Ver [docs/DEMO.md](docs/DEMO.md): caso 1 (integración exitosa), caso 2 (fallo de pruebas) y
anexo UAT (error encontrado en UAT y aprobación).
