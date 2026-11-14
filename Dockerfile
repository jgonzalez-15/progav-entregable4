# Imagen de ejecución de la aplicación (el JAR lo construye el pipeline con Maven).
FROM eclipse-temurin:21-jre-alpine

ARG APP_VERSION=local
ARG APP_COMMIT=unknown
LABEL org.opencontainers.image.title="rps3d" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${APP_COMMIT}"

# La versión queda "horneada" en la imagen: la misma imagen que se aprueba en UAT es la que llega a PROD
ENV APP_VERSION=${APP_VERSION} \
    APP_COMMIT=${APP_COMMIT} \
    APP_DATA_DIR=/data \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

RUN addgroup -S app && adduser -S app -G app && mkdir -p /data && chown app:app /data
WORKDIR /app
COPY --chown=app:app web/target/rps3d.jar app.jar
USER app
VOLUME /data
EXPOSE 8090

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
