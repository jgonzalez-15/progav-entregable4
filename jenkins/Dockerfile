# Jenkins LTS con JDK 21 + cliente Docker para que el pipeline cree los contenedores UAT/PROD.
# El CLI de Docker es un binario estático: lo copiamos de la imagen oficial (sin apt, build más rápido).
FROM docker:29-cli AS dockercli

FROM jenkins/jenkins:lts-jdk21
USER root
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=dockercli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx

# Plugins y configuración como código (sin asistente inicial)
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY casc.yaml /usr/share/jenkins/ref/casc.yaml
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/casc.yaml \
    JAVA_OPTS="-Djenkins.install.runSetupWizard=false"

# Se ejecuta como root para poder usar /var/run/docker.sock del host
# (en Docker Desktop el socket pertenece a root). Ver informe, sección "Fallos".
