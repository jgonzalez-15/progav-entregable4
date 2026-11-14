# Jenkins en Docker + webhook de GitHub (paso a paso)

Pensado para Docker Desktop (Windows con WSL2 y macOS) y Docker Engine en Linux.
Todos los comandos se ejecutan desde la raíz del repositorio.

## 0. Requisitos

1. **Docker Desktop** instalado y corriendo (`docker version` debe mostrar *Server*).
2. Cuenta en **GitHub** con el repositorio creado y el código subido (ver paso 1).
3. Cuenta gratuita en **ngrok** (https://dashboard.ngrok.com/signup) para recibir webhooks en tu PC.
   - Copiá el *authtoken* desde https://dashboard.ngrok.com/get-started/your-authtoken
   - (Recomendado) Reclamá tu **dominio estático gratuito** en https://dashboard.ngrok.com/domains.
     Así la URL del webhook no cambia cada vez que reiniciás.

## 1. Subir el código a GitHub

```bash
git remote add origin https://github.com/OWNER/piedra-papel-tijera-3d.git
git push -u origin main
```

Si el repositorio es **público**, Jenkins no necesita credenciales. Si es **privado**, generá un
token (GitHub → Settings → Developer settings → Personal access tokens → *Fine-grained*, con
permiso **Contents: Read-only** sobre el repo) y cargalo en `GITHUB_USER` / `GITHUB_TOKEN`.

## 2. Configurar variables

macOS / Linux:

```bash
cp jenkins/.env.example jenkins/.env
```

Windows (PowerShell):

```powershell
Copy-Item jenkins\.env.example jenkins\.env
notepad jenkins\.env
```

Completar como mínimo:

```ini
JENKINS_ADMIN_PASSWORD=una-clave-segura
JENKINS_QA_PASSWORD=otra-clave
REPO_URL=https://github.com/OWNER/piedra-papel-tijera-3d.git
NGROK_AUTHTOKEN=<tu authtoken>
NGROK_EXTRA_ARGS=--url=https://tu-dominio.ngrok-free.app
JENKINS_URL=https://tu-dominio.ngrok-free.app/
```

## 3. Levantar Jenkins (+ túnel)

macOS / Linux:

```bash
./scripts/setup-jenkins.sh --tunnel
```

Windows:

```powershell
.\scripts\setup-jenkins.ps1 -Tunnel
```

Equivalente manual:

```bash
docker compose -f jenkins/docker-compose.yml --profile tunnel up -d --build
docker logs -f rps-jenkins        # esperar "Jenkins is fully up and running"
```

Qué pasa al arrancar:

- Se construye la imagen `rps-jenkins:lts` (Jenkins LTS + JDK 21 + CLI de Docker + plugins).
- **JCasC** crea los usuarios `admin` y `qa`, sin asistente inicial.
- **Job DSL** crea el job `piedra-papel-tijera-3d`, que apunta a `REPO_URL` y usa el `Jenkinsfile`
  del repo, con los triggers *GitHub push* y *Poll SCM* ya activos.
- Jenkins monta `/var/run/docker.sock`, así que los contenedores `rps-uat` y `rps-prod` se crean
  en el Docker del host, en la red `rps-net`.

Abrí http://localhost:8080 e ingresá con `admin` y la clave del `.env`.

URL pública del túnel: http://localhost:4040 (panel de ngrok) o `docker logs rps-ngrok`.

## 4. Crear el webhook en GitHub

Repositorio → **Settings → Webhooks → Add webhook**:

| Campo | Valor |
|---|---|
| Payload URL | `https://tu-dominio.ngrok-free.app/github-webhook/` (**con la barra final**) |
| Content type | `application/json` |
| Secret | (vacío) |
| SSL verification | Enable |
| Which events | *Just the push event* |
| Active | ✔ |

Con GitHub CLI (alternativa):

```bash
gh api repos/OWNER/piedra-papel-tijera-3d/hooks \
  -f name=web -F active=true -f 'events[]=push' \
  -f 'config[url]=https://tu-dominio.ngrok-free.app/github-webhook/' \
  -f 'config[content_type]=json'
```

Verificación: en **Recent Deliveries** el *ping* debe responder `200`. Después de un `git push`,
Jenkins muestra “Started by GitHub push by …” en el build.

> Si ngrok no está disponible durante la demo, el job igual se dispara por **polling** cada ~2 minutos.

## 5. Primera ejecución

1. El primer push dispara el pipeline. También podés usar **Build Now** una vez.
2. Las etapas se ven en *Stage View* (página del job) o en *Pipeline Overview* (`pipeline-graph-view`).
3. Al llegar a **Aprobación QA (UAT)** el build queda en pausa:
   - Probá manualmente UAT en http://localhost:8081 (el badge naranja dice `UAT · v1.0.N`).
   - En Jenkins, hacé clic en la etapa en pausa (o abrí `http://localhost:8080/job/piedra-papel-tijera-3d/lastBuild/input/`),
     elegí **Aprobar** o **Rechazar**, escribí notas y apretá **Enviar decisión**.
4. Si se aprueba, se despliega la misma imagen en PROD (http://localhost:8082, badge verde).

Ver qué versión corre en cada ambiente:

```bash
./scripts/status.sh
```

```powershell
.\scripts\status.ps1
```

## 6. Operación

| Acción | Comando |
|---|---|
| Logs de Jenkins | `docker logs -f rps-jenkins` |
| Logs de UAT / PROD | `docker logs -f rps-uat` / `docker logs -f rps-prod` |
| Rollback manual de PROD | `docker exec rps-jenkins bash -c "cd /var/jenkins_home/workspace/piedra-papel-tijera-3d && scripts/rollback.sh prod"` (Windows: `.\scripts\rollback.ps1 prod`) |
| Apagar Jenkins | `docker compose -f jenkins/docker-compose.yml --profile tunnel down` |
| Borrar todo (incl. datos) | `docker compose -f jenkins/docker-compose.yml --profile tunnel down -v && docker rm -f rps-uat rps-prod` |

## 7. Problemas frecuentes

| Síntoma | Causa | Solución |
|---|---|---|
| `permission denied ... /var/run/docker.sock` | El usuario `jenkins` no puede usar el socket | El compose ejecuta el contenedor con `user: root` |
| `/usr/bin/env: 'bash\r': No such file or directory` | Scripts con fin de línea CRLF (clonados en Windows) | `.gitattributes` fuerza LF en `*.sh`. Si ya clonaste: `git rm --cached -r . && git reset --hard` |
| `./mvnw: Permission denied` | Se perdió el bit de ejecución al commitear desde Windows | El pipeline hace `chmod +x mvnw scripts/*.sh`. Opcional: `git update-index --chmod=+x mvnw scripts/*.sh` |
| El webhook devuelve 302/403 | Falta la barra final en `/github-webhook/` o el túnel apunta a otro puerto | Usar exactamente `https://…/github-webhook/` |
| El push no dispara nada | El webhook no llega (túnel caído) | Revisar *Recent Deliveries*. El polling lo dispara igual en ≤ 2 min |
| Smoke test: `Connection refused` a `localhost:8081` desde Jenkins | Dentro del contenedor, `localhost` es Jenkins, no el host | Se usa la red `rps-net` y los nombres `rps-uat` / `rps-prod` (configurable con `RPS_UAT_URL` / `RPS_PROD_URL`) |
| Puerto 8080 ocupado | Otro servicio local | Cambiar `"8080:8080"` por `"9090:8080"` en `jenkins/docker-compose.yml` |
