# Guion de la defensa (≈ 10 minutos)

Antes de empezar: Jenkins y ngrok corriendo (`scripts/setup-jenkins.*`), un build exitoso previo
en PROD y tres pestañas abiertas: Jenkins (8080), UAT (8081) y PROD (8082).

## 1. Arquitectura y pipeline (2 min)

- Mostrar el diagrama del informe: módulos `game-core` → `rooms` → `web`, ambientes DEV/UAT/PROD.
- Abrir el `Jenkinsfile` y recorrer las etapas.

## 2. La aplicación (1 min)

- PROD (http://localhost:8082): la cámara del menú gira por la oficina. Jugar 2 rondas contra la CPU.
- Multijugador: crear sala en una ventana y unirse con el código desde otra (o desde un celular en la misma red).

## 3. Caso 1 — Integración exitosa (3 min)

Cambio visible y seguro, por ejemplo el subtítulo del menú:

```bash
# web/src/main/resources/static/index.html
#   <p class="subtitle">Duelo de oficina en 3D</p>  ->  <p class="subtitle">Duelo de oficina en 3D — ¡Demo en vivo!</p>
git checkout -b feature/subtitulo-demo
git commit -am "Cambiar subtítulo del menú para la demo"
git checkout main && git merge --no-ff feature/subtitulo-demo
git push origin main
```

1. En GitHub → Webhooks → *Recent Deliveries* aparece el push con respuesta 200.
2. Jenkins arranca solo (“Started by GitHub push”). Las etapas van quedando en verde.
3. Se pausa en **Aprobación QA**. Mostrar UAT con el cambio (badge `UAT · v1.0.N`) y PROD todavía sin el cambio.
4. **Aprobar** en Jenkins (usuario `qa`). PROD se actualiza: recargar http://localhost:8082 y verificar el subtítulo y la versión.

## 4. Caso 2 — Fallo en las pruebas (2 min)

Introducir un bug en las reglas:

```java
// game-core/src/main/java/uy/edu/um/rps/core/Move.java
case PAPER -> other == SCISSORS;   // antes: other == ROCK
```

```bash
git commit -am "Bug intencional: papel le gana a tijera"
git push origin main
```

1. El pipeline falla en **Pruebas unitarias**: 5 fallas en `GameRulesTest`. El reporte JUnit de
   Jenkins muestra qué combinación esperaba `WIN` y recibió `LOSE`.
2. Las etapas siguientes quedan omitidas: **nada se despliega**. `scripts/status.*` muestra UAT y PROD con la versión anterior.
3. Cómo se procede: identificar el commit responsable, corregir o revertir, y volver a pushear.

```bash
git revert --no-edit HEAD
git push origin main     # el pipeline vuelve a verde
```

## 5. Anexo — Error encontrado en UAT (2 min)

Un bug que las pruebas automáticas **no** detectan (visual), por ejemplo intercambiar etiquetas:

```html
<!-- index.html: en los botones de jugada -->
<button class="move" data-move="ROCK"><span class="emoji">✊</span><span>Papel</span></button>
```

1. Push → pipeline en verde hasta UAT → pausa en **Aprobación QA**.
2. QA prueba en UAT y detecta que el botón de piedra dice “Papel”.
3. En Jenkins: **Rechazar**, con la nota “Botón Piedra muestra texto Papel”.
   - El build queda **UNSTABLE** (amarillo), con descripción “Rechazada en UAT por qa: …”.
   - Se archiva `uat-rechazo.txt` con versión, commit, autor y notas.
   - **Producción no cambia** (verificar con `scripts/status.*`).
4. Acciones a tomar (también en el informe):
   1. Crear un issue en GitHub con la nota de QA y la versión rechazada.
   2. Corregir en una rama `fix/…`, idealmente agregando una prueba que cubra el caso.
   3. Merge a `main`: nuevo pipeline, nueva versión en UAT y re-test por QA.
   4. Si UAT quedó inutilizable para otras pruebas: `scripts/rollback.sh uat`.
   5. Recién con la aprobación, la versión corregida pasa a producción.

## 6. (V2) Competitivo, ranking y celebraciones

1. En UAT (http://localhost:8081, con 150 jugadores de demo) abrir **📊 Ranking**: se ve el Top 100.
2. Ventana A: **🎉 Celebraciones** → “Paz” → *Vista previa 3D* → *Guardar*. Después **🏆 Competitivo**.
3. Ventana B (incógnito): otro nombre → **🏆 Competitivo**. Quedan emparejados (0 victorias cada uno).
4. Ganar 2 rondas con A: la mano de A hace el signo de la paz sobre el escritorio (B también lo ve).
5. B abre el ranking: aparece **fuera del Top 100**, con su posición exacta fija abajo.
6. Antifraude: jugar contra la CPU y volver al ranking. Las victorias contra la CPU no suman.
