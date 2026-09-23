pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        PROJECT_NAME = 'react-job-portal'
        COMPOSE_PROJECT = 'react-job-portal'

        BACKEND_IMAGE = "react-job-portal-backend:${BUILD_NUMBER}"
        FRONTEND_IMAGE = "react-job-portal-frontend:${BUILD_NUMBER}"

        BACKEND_PORT = '4000'
        FRONTEND_PORT = '5173'

        FRONTEND_URL = 'http://localhost:5173'
        DB_URL = 'mongodb://mongodb:27017/Job_Portal'

        JWT_EXPIRE = '7d'
        COOKIE_EXPIRE = '7'
        JWT_SECRET_KEY = "ci-only-secret-${BUILD_NUMBER}"

        CLOUDINARY_CLOUD_NAME = 'ci_dummy_cloud'
        CLOUDINARY_API_KEY = '123456789012345'
        CLOUDINARY_API_SECRET = 'ci_dummy_secret'
    }

    stages {
        stage('Clean Workspace') {
            steps {
                deleteDir()
            }
        }

        stage('Clone Repository') {
            steps {
                echo 'Cloning react-job-portal main branch'
                git branch: 'main', url: 'https://github.com/exclusiveabhi/react-job-portal.git'

                sh '''
                    set -eu
                    echo "Commit: $(git rev-parse --short HEAD)"
                    git status --short
                    echo '--- repository root ---'
                    ls -lah
                '''
            }
        }

        stage('Preflight & Validate Compose') {
            steps {
                sh '''
                    set -eu

                    docker --version
                    docker compose version
                    curl --version | head -n 1

                    test -f Jenkinsfile
                    test -f backend/Dockerfile
                    test -f frontend/Dockerfile
                    test -f docker-compose.jenkins.yml
                    test -f backend/package-lock.json
                    test -f frontend/package-lock.json

                    export BACKEND_IMAGE="$BACKEND_IMAGE"
                    export FRONTEND_IMAGE="$FRONTEND_IMAGE"
                    export FRONTEND_URL="$FRONTEND_URL"
                    export DB_URL="$DB_URL"
                    export JWT_SECRET_KEY="$JWT_SECRET_KEY"
                    export JWT_EXPIRE="$JWT_EXPIRE"
                    export COOKIE_EXPIRE="$COOKIE_EXPIRE"
                    export CLOUDINARY_CLOUD_NAME="$CLOUDINARY_CLOUD_NAME"
                    export CLOUDINARY_API_KEY="$CLOUDINARY_API_KEY"
                    export CLOUDINARY_API_SECRET="$CLOUDINARY_API_SECRET"

                    docker compose -f docker-compose.jenkins.yml config >/tmp/react-job-portal-compose.yml
                    echo 'Compose configuration is valid.'
                '''
            }
        }

        stage('Backend CI') {
            steps {
                dir('backend') {
                    sh '''
                        set -eu
                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e NPM_CONFIG_CACHE=/tmp/npm-cache \
                            -v "$PWD:/app" \
                            -w /app \
                            node:22-alpine \
                            sh -c "npm ci && node --check server.js"
                    '''
                }
            }
        }

        stage('Frontend CI') {
            steps {
                dir('frontend') {
                    sh '''
                        set -eu
                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e NPM_CONFIG_CACHE=/tmp/npm-cache \
                            -v "$PWD:/app" \
                            -w /app \
                            node:22-alpine \
                            sh -c "npm ci && npm run build && npm run lint"
                    '''
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                sh '''
                    set -eu

                    docker build -t "$BACKEND_IMAGE" ./backend
                    docker build -t "$FRONTEND_IMAGE" ./frontend

                    echo '--- built images ---'
                    docker images --format 'table {{.Repository}}\\t{{.Tag}}\\t{{.Size}}' | grep -E 'react-job-portal-(backend|frontend)' || true
                '''
            }
        }

        stage('Deploy MongoDB') {
            steps {
                sh '''
                    set -eu

                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" up -d --force-recreate --no-build mongodb
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" ps
                '''
            }
        }

        stage('Deploy Backend') {
            steps {
                sh '''
                    set -eu

                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" up -d --force-recreate --no-build backend
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" ps
                '''
            }
        }

        stage('Deploy Frontend') {
            steps {
                sh '''
                    set -eu

                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" up -d --force-recreate --no-build frontend
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" ps
                '''
            }
        }

        stage('Wait & Smoke Test') {
            steps {
                sh '''
                    set -eu

                    echo 'Waiting for frontend and backend...'

                    frontend_ok=0
                    backend_ok=0

                    for i in $(seq 1 60); do
                        if curl -fsS http://127.0.0.1:5173/ >/dev/null 2>&1; then
                            frontend_ok=1
                            echo "Frontend is ready after ${i} checks"
                            break
                        fi
                        sleep 2
                    done

                    for i in $(seq 1 60); do
                        if curl -fsS http://127.0.0.1:4000/api/v1/job/getall >/dev/null 2>&1; then
                            backend_ok=1
                            echo "Backend API is ready after ${i} checks"
                            break
                        fi
                        sleep 2
                    done

                    test "$frontend_ok" -eq 1
                    test "$backend_ok" -eq 1

                    echo '--- compose status ---'
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" ps

                    echo '--- backend logs ---'
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" logs --tail=100 backend

                    echo '--- frontend logs ---'
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" logs --tail=100 frontend

                    echo '--- mongodb logs ---'
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" logs --tail=100 mongodb

                    echo 'FULL STACK SMOKE TEST PASSED'
                '''
            }
        }
    }

    post {
        always {
            sh '''
                set +e
                if [ -f docker-compose.jenkins.yml ]; then
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" ps
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" logs --tail=120
                fi
            '''
        }

        success {
            echo 'Jenkins pipeline completed successfully.'
            echo 'Frontend: http://localhost:5173'
            echo 'Backend:  http://localhost:4000'
        }

        failure {
            echo 'Pipeline failed. Review the failed stage and the compose logs above.'
            sh '''
                set +e
                if [ -f docker-compose.jenkins.yml ]; then
                    docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" down --remove-orphans
                fi
            '''
        }
    }
}
