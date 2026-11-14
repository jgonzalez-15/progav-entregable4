// Pipeline declarativo: Build -> Pruebas -> Imagen -> UAT -> Aprobación manual -> Producción
// Se ejecuta automáticamente con cada push a GitHub (webhook) y, como respaldo, por polling.
pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 4, unit: 'HOURS')
    }

    triggers {
        githubPush()              // webhook de GitHub -> /github-webhook/
        pollSCM('H/2 * * * *')    // respaldo si el webhook no llega (túnel caído, etc.)
    }

    environment {
        IMAGE        = 'rps3d'
        MVN          = './mvnw -B -ntp'
        // Jenkins y los ambientes comparten la red Docker rps-net (configurable por variable de entorno)
        UAT_URL      = "${env.RPS_UAT_URL ?: 'http://rps-uat:8090'}"
        PROD_URL     = "${env.RPS_PROD_URL ?: 'http://rps-prod:8090'}"
        UAT_PUBLIC   = 'http://localhost:8081'
        PROD_PUBLIC  = 'http://localhost:8082'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x mvnw scripts/*.sh'
                script {
                    // Versión = <major.minor del pom>.<número de build>  (ej. 2.0.17)
                    def pomVersion = sh(script: './mvnw -q -ntp help:evaluate -Dexpression=project.version -DforceStdout',
                                        returnStdout: true).trim()
                    def parts = pomVersion.tokenize('.')
                    env.VERSION = "${parts[0]}.${parts[1]}.${env.BUILD_NUMBER}"
                    env.GIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.GIT_AUTHOR = sh(script: 'git log -1 --pretty=%an', returnStdout: true).trim()
                    currentBuild.displayName = "#${env.BUILD_NUMBER} v${env.VERSION} (${env.GIT_SHORT})"
                }
            }
        }

        stage('Build') {
            steps {
                sh "${MVN} clean package -DskipTests"
            }
        }

        stage('Pruebas unitarias') {
            steps {
                sh "${MVN} test"
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Pruebas de integración') {
            steps {
                // -DskipUTs: las unitarias ya corrieron; failsafe ejecuta los *IT y empaqueta el JAR
                sh "${MVN} verify -DskipUTs=true"
            }
            post {
                always {
                    junit testResults: '**/target/failsafe-reports/*.xml', allowEmptyResults: true
                }
                success {
                    archiveArtifacts artifacts: 'web/target/rps3d.jar', fingerprint: true
                }
            }
        }

        stage('Imagen Docker') {
            steps {
                sh """
                    docker build \
                      --build-arg APP_VERSION=${VERSION} \
                      --build-arg APP_COMMIT=${GIT_SHORT} \
                      -t ${IMAGE}:${VERSION} .
                """
            }
        }

        stage('Deploy UAT') {
            steps {
                sh "scripts/deploy.sh uat ${IMAGE}:${VERSION}"
                sh "scripts/smoke-test.sh ${UAT_URL} UAT ${VERSION}"
            }
            post {
                failure {
                    echo 'Falló el despliegue/smoke test en UAT: se restaura la versión anterior de UAT'
                    sh 'scripts/rollback.sh uat || true'
                }
            }
        }

        stage('Aprobación QA (UAT)') {
            steps {
                script {
                    echo "Versión ${VERSION} lista para pruebas manuales en UAT: ${UAT_PUBLIC}"
                    def decision
                    // El pipeline queda en pausa hasta que alguien decida en la UI de Jenkins
                    timeout(time: 2, unit: 'HOURS') {
                        decision = input(
                            id: 'aprobacion-prod',
                            message: "¿Aprobar la instalación en PRODUCCIÓN de la versión ${VERSION} (${GIT_SHORT})?",
                            ok: 'Enviar decisión',
                            submitterParameter: 'APROBADOR',
                            parameters: [
                                choice(name: 'DECISION', choices: ['Aprobar', 'Rechazar'],
                                       description: 'Resultado de las pruebas manuales en UAT (' + env.UAT_PUBLIC + ')'),
                                text(name: 'NOTAS_QA', defaultValue: '',
                                     description: 'Casos probados / descripción del error encontrado')
                            ]
                        )
                    }
                    env.DECISION = decision.DECISION
                    env.APROBADOR = decision.APROBADOR
                    env.NOTAS_QA = decision.NOTAS_QA ?: ''
                    if (env.DECISION == 'Rechazar') {
                        writeFile file: 'uat-rechazo.txt', text: """Versión rechazada en UAT
Versión: ${VERSION}
Commit: ${GIT_SHORT} (${GIT_AUTHOR})
Rechazado por: ${env.APROBADOR}
Notas de QA: ${env.NOTAS_QA}
Acciones: crear issue en GitHub, corregir en una rama fix/*, merge a main -> nuevo pipeline -> nueva prueba en UAT.
Producción NO fue modificada.
"""
                        archiveArtifacts artifacts: 'uat-rechazo.txt'
                        currentBuild.description = "Rechazada en UAT por ${env.APROBADOR}: ${env.NOTAS_QA}"
                        unstable("Versión ${VERSION} rechazada en UAT. Producción no se modifica.")
                    } else {
                        currentBuild.description = "Aprobada por ${env.APROBADOR}"
                    }
                }
            }
        }

        stage('Deploy Producción') {
            when {
                expression { env.DECISION == 'Aprobar' }
            }
            steps {
                sh "scripts/deploy.sh prod ${IMAGE}:${VERSION}"
                sh "scripts/smoke-test.sh ${PROD_URL} PROD ${VERSION}"
                script {
                    currentBuild.description = "En PRODUCCIÓN — aprobada por ${env.APROBADOR}"
                }
            }
            post {
                failure {
                    echo 'Falló el smoke test en PRODUCCIÓN: rollback automático a la versión anterior'
                    sh 'scripts/rollback.sh prod || true'
                }
            }
        }
    }

    post {
        success {
            echo "Pipeline OK: v${VERSION} en UAT (${UAT_PUBLIC}) y PRODUCCIÓN (${PROD_PUBLIC})"
        }
        unstable {
            echo 'Pipeline UNSTABLE: la versión fue rechazada en UAT; ver uat-rechazo.txt'
        }
        failure {
            echo 'Pipeline FALLIDO: revisar la etapa en rojo y los reportes de pruebas. Nada llegó a producción.'
        }
        aborted {
            echo 'Pipeline ABORTADO (aprobación cancelada o tiempo agotado). Producción no se modificó.'
        }
    }
}
