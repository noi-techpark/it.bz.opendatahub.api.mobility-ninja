pipeline {
    agent any

    environment {
        DOCKER_PROJECT_NAME = "ninja"
        DOCKER_IMAGE = '755952719952.dkr.ecr.eu-west-1.amazonaws.com/ninja'
        DOCKER_TAG = "prod-$BUILD_NUMBER"

        SERVER_PORT = "1004"
        NINJA_HOST_URL = "https://mobility.api.opendatahub.bz.it"
        NINJA_BASE_URL = "${NINJA_HOST_URL}/v2"
        NINJA_QUERY_TIMEOUT_SEC = "30"
        NINJA_RESPONSE_MAX_SIZE_MB = "100"
        LOG_APPLICATION_NAME = "ninja"
        LOG_APPLICATION_VERSION = "0.8.2"

        SECURITY_ALLOWED_ORIGINS = "*"
        KEYCLOAK_URL = "https://auth.opendatahub.opendatahub.bz.it/auth/"
        KEYCLOAK_SSL_REQUIRED = "none"
        KEYCLOAK_REALM = "noi"
        KEYCLOAK_CLIENT_ID = "odh-mobility-v2"
        KEYCLOAK_CLIENT_SECRET = credentials('ninja-prod-keycloak-client-secret')

        JDBC_URL = "jdbc:postgresql://prod-pg-bdp.co90ybcr8iim.eu-west-1.rds.amazonaws.com:5432/bdp?currentSchema=intimev2,public"
        DB_USERNAME = "bdp_readonly"
        DB_PASSWORD = credentials('bdp-core-prod-database-read-password')
    }

    stages {
        stage('Configure') {
            steps {
                sh """
                    rm -f .env
                    cp .env.example .env
                    echo 'COMPOSE_PROJECT_NAME=${DOCKER_PROJECT_NAME}' >> .env
                    echo 'DOCKER_IMAGE=${DOCKER_IMAGE}' >> .env
                    echo 'DOCKER_TAG=${DOCKER_TAG}' >> .env
                    echo 'SERVER_PORT=${SERVER_PORT}' >> .env
                    echo 'NINJA_HOST_URL=${NINJA_HOST_URL}' >> .env
                    echo 'NINJA_BASE_URL=${NINJA_BASE_URL}' >> .env
                    echo 'NINJA_QUERY_TIMEOUT_SEC=${NINJA_QUERY_TIMEOUT_SEC}' >> .env
                    echo 'NINJA_RESPONSE_MAX_SIZE_MB=${NINJA_RESPONSE_MAX_SIZE_MB}' >> .env
                    echo 'SECURITY_ALLOWED_ORIGINS=${SECURITY_ALLOWED_ORIGINS}' >> .env
                    echo 'KEYCLOAK_URL=${KEYCLOAK_URL}' >> .env
                    echo 'KEYCLOAK_SSL_REQUIRED=${KEYCLOAK_SSL_REQUIRED}' >> .env
                    echo 'KEYCLOAK_REALM=${KEYCLOAK_REALM}' >> .env
                    echo 'KEYCLOAK_CLIENT_ID=${KEYCLOAK_CLIENT_ID}' >> .env
                    echo 'KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET}' >> .env
                    echo 'LOG_APPLICATION_NAME=${LOG_APPLICATION_NAME}' >> .env
                    echo 'LOG_APPLICATION_VERSION=${LOG_APPLICATION_VERSION}' >> .env
                    echo 'JDBC_URL=${JDBC_URL}' >> .env
                    echo 'DB_USERNAME=${DB_USERNAME}' >> .env
                    echo 'DB_PASSWORD=${DB_PASSWORD}' >> .env
                """
            }
        }
        stage('Test') {
            steps {
                sh '''
                    docker network create authentication || true
                    docker-compose --no-ansi build --pull --build-arg JENKINS_USER_ID=$(id -u jenkins) --build-arg JENKINS_GROUP_ID=$(id -g jenkins)
                    docker-compose --no-ansi run --rm --no-deps -u $(id -u jenkins):$(id -g jenkins) app mvn -B clean test
                '''
            }
        }
        stage('Build') {
            steps {
                sh '''
                    aws ecr get-login --region eu-west-1 --no-include-email | bash
                    docker-compose --no-ansi -f infrastructure/docker-compose.build.yml build --pull
                    docker-compose --no-ansi -f infrastructure/docker-compose.build.yml push
                '''
            }
        }
        stage('Deploy') {
            steps {
               sshagent(['jenkins-ssh-key']) {
                    sh """
                        cd infrastructure/ansible
                        ansible-galaxy install -f -r requirements.yml
                        ansible-playbook --limit=prod deploy.yml --extra-vars "release_name=${BUILD_NUMBER}"
                    """
                }
            }
        }
    }
}
