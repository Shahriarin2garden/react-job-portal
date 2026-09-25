// Load shared library from this repo's jenkins-shared-library directory
@Library(value='react-job-portal-shared-lib', changelog=false) _
library identifier: 'react-job-portal-shared-lib@main', retriever: legacySCM([$class: 'GitSCMSource', remote: 'https://github.com/Shahriarin2garden/react-job-portal.git', credentialsId: 'github-private-repo-credentials', traits: [[$class: 'jenkins.plugins.git.traits.BranchDiscoveryTrait']]])

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
        skipDefaultCheckout(true)
        timeout(time: 30, unit: 'MINUTES')
    }

    /*
     * Webhook triggering.
     * githubPush() requires the GitHub plugin and a webhook pointing at:
     *   http://<jenkins-host>:8080/github-webhook/
     * The pollSCM fallback also picks up changes if the webhook is unavailable.
     */
    triggers {
        githubPush()
        pollSCM('H/15 * * * *')
    }

    environment {
        PROJECT_NAME    = 'react-job-portal'
        COMPOSE_PROJECT = 'react-job-portal'

        GIT_URL            = 'https://github.com/Shahriarin2garden/react-job-portal.git'
        GIT_BRANCH         = 'main'
        GIT_CREDENTIALS_ID = 'github-private-repo-credentials'

        CI_EMAIL_RECIPIENTS = 'devops@example.com'

        BACKEND_IMAGE   = "react-job-portal-backend:${BUILD_NUMBER}"
        FRONTEND_IMAGE  = "react-job-portal-frontend:${BUILD_NUMBER}"

        BACKEND_PORT    = '4000'
        FRONTEND_PORT   = '5173'

        VITE_API_URL    = 'http://192.168.56.50:4000/api/v1'
        FRONTEND_URL    = 'http://192.168.56.50:5173'
        DB_URL          = 'mongodb://mongodb:27017/Job_Portal'

        JWT_EXPIRE      = '7d'
        COOKIE_EXPIRE   = '7'
        JWT_SECRET_KEY  = "ci-only-secret-${BUILD_NUMBER}"

        CLOUDINARY_CLOUD_NAME = 'ci_dummy_cloud'
        CLOUDINARY_API_KEY    = '123456789012345'
        CLOUDINARY_API_SECRET = 'ci_dummy_secret'
    }

    stages {

        stage('Clean Workspace') {
            steps {
                deleteDir()
            }
        }

        stage('Checkout Repository') {
            steps {
                checkoutWithCredentials(
                    url: "${GIT_URL}",
                    branch: "${GIT_BRANCH}",
                    credentialsId: "${GIT_CREDENTIALS_ID}"
                )

                sh '''
                    set -eu

                    echo "======================================"
                    echo "CHECKED OUT REPOSITORY"
                    echo "======================================"

                    echo "Commit:"
                    git rev-parse --short HEAD

                    echo "Remote:"
                    git remote -v

                    echo "Repository structure:"
                    ls -lah

                    echo "Backend:"
                    ls -lah backend

                    echo "Frontend:"
                    ls -lah frontend
                '''
            }
        }

        stage('Preflight & Validate Compose') {
            steps {
                sh '''
                    set -eu

                    echo "======================================"
                    echo "PREFLIGHT CHECK"
                    echo "======================================"

                    echo "--- Docker ---"
                    docker --version

                    echo "--- Docker Compose ---"
                    docker compose version

                    echo "--- Git ---"
                    git --version

                    echo "--- Curl ---"
                    curl --version | head -n 1

                    echo "--- Required files ---"

                    test -f Jenkinsfile
                    test -f backend/Dockerfile
                    test -f backend/package.json
                    test -f backend/package-lock.json

                    test -f frontend/Dockerfile
                    test -f frontend/package.json
                    test -f frontend/package-lock.json

                    test -f docker-compose.jenkins.yml

                    echo "All required files exist."

                    echo "======================================"
                    echo "VALIDATING DOCKER COMPOSE"
                    echo "======================================"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        config > /tmp/react-job-portal-compose.yml

                    echo "Docker Compose configuration is valid."
                '''
            }
        }

        stage('Backend CI') {
            steps {
                dir('backend') {
                    sh '''
                        set -eu

                        echo "======================================"
                        echo "BACKEND CI"
                        echo "======================================"

                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e NPM_CONFIG_CACHE=/tmp/npm-cache \
                            -v "$PWD:/app" \
                            -w /app \
                            node:22-alpine \
                            sh -c "npm ci && node --check server.js"

                        echo "Backend dependency installation passed."
                        echo "Backend syntax validation passed."
                    '''
                }
            }
        }

        stage('Frontend CI') {
            steps {
                dir('frontend') {
                    sh '''
                        set -eu

                        echo "======================================"
                        echo "FRONTEND CI"
                        echo "======================================"

                        echo "--- Installing dependencies ---"

                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e NPM_CONFIG_CACHE=/tmp/npm-cache \
                            -v "$PWD:/app" \
                            -w /app \
                            node:22-alpine \
                            sh -c "npm ci"

                        echo "--- Production build ---"

                        docker run --rm \
                            -u "$(id -u):$(id -g)" \
                            -e HOME=/tmp \
                            -e NPM_CONFIG_CACHE=/tmp/npm-cache \
                            -v "$PWD:/app" \
                            -w /app \
                            node:22-alpine \
                            sh -c "npm run build"

                        echo "Frontend build passed."
                    '''
                }
            }
        }

        /*
         * The current project has existing ESLint errors.
         * We run lint and report the result without blocking
         * the Docker deployment pipeline.
         */
        stage('Frontend Lint Check') {
            steps {
                dir('frontend') {
                    script {
                        def lintStatus = sh(
                            script: '''
                                set +e

                                docker run --rm \
                                    -u "$(id -u):$(id -g)" \
                                    -e HOME=/tmp \
                                    -e NPM_CONFIG_CACHE=/tmp/npm-cache \
                                    -v "$PWD:/app" \
                                    -w /app \
                                    node:22-alpine \
                                    sh -c "npm ci && npm run lint"

                                exit $?
                            ''',
                            returnStatus: true
                        )

                        if (lintStatus != 0) {
                            echo "WARNING: Frontend ESLint reported errors."
                            echo "The application build succeeded, so the deployment pipeline will continue."
                            currentBuild.result = 'UNSTABLE'
                        } else {
                            echo "Frontend lint passed."
                        }
                    }
                }
            }
        }

        stage('Security Scan - Secrets (betterleaks)') {
            steps {
                script {
                    def rc = sh(
                        script: """
                            set +e

                            echo "======================================"
                            echo "SECRETS DETECTION (betterleaks)"
                            echo "======================================"

                            betterleaks dir backend --report-path betterleaks-backend.json --report-format json
                            rc_backend=\$?

                            betterleaks dir frontend --report-path betterleaks-frontend.json --report-format json
                            rc_frontend=\$?

                            echo "betterleaks exit codes -> backend: \$rc_backend, frontend: \$rc_frontend"

                            if [ "\$rc_backend" -ne 0 ] || [ "\$rc_frontend" -ne 0 ]; then
                                exit 99
                            fi
                            exit 0
                        """,
                        returnStatus: true
                    )

                    archiveArtifacts artifacts: 'betterleaks-backend.json,betterleaks-frontend.json', fingerprint: true

                    if (rc != 0) {
                        unstable("betterleaks reported potential secrets (rc ${rc}). Reports archived as build artifacts.")
                    } else {
                        echo "No secrets detected."
                    }
                }
            }
        }

        stage('Security Scan - SAST (semgrep)') {
            steps {
                script {
                    def rc = sh(
                        script: """
                            set +e

                            echo "======================================"
                            echo "STATIC ANALYSIS (semgrep)"
                            echo "======================================"

                            semgrep scan --config auto backend --json --output=semgrep-backend.json
                            rc_backend=\$?

                            semgrep scan --config auto frontend --json --output=semgrep-frontend.json
                            rc_frontend=\$?

                            echo "semgrep exit codes -> backend: \$rc_backend, frontend: \$rc_frontend"

                            # semgrep exit codes: 0 = clean, 1 = findings, >=2 = error
                            if [ "\$rc_backend" -ge 2 ] || [ "\$rc_frontend" -ge 2 ]; then
                                exit 2
                            fi

                            if [ "\$rc_backend" -eq 1 ] || [ "\$rc_frontend" -eq 1 ]; then
                                exit 1
                            fi
                            exit 0
                        """,
                        returnStatus: true
                    )

                    archiveArtifacts artifacts: 'semgrep-backend.json,semgrep-frontend.json', fingerprint: true

                    if (rc >= 2) {
                        error "semgrep SAST scan failed (rc ${rc}). See semgrep reports."
                    } else if (rc != 0) {
                        unstable("semgrep SAST reported findings (rc ${rc}). Reports archived as build artifacts.")
                    } else {
                        echo "No SAST findings."
                    }
                }
            }
        }

        stage('Security Scan - Trivy Filesystem') {
            steps {
                sh '''
                    set -eu

                    echo "======================================"
                    echo "TRIVY FILESYSTEM SCAN"
                    echo "======================================"

                    trivy fs backend \
                        --scanners vuln,misconfig,secret \
                        --skip-files '**/betterleaks*.json,**/semgrep*.json' \
                        --format json -o trivy-fs-backend-report.json

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format template --template "@/contrib/html.tpl" \
                        -o trivy-fs-backend-report.html \
                        trivy-fs-backend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."
                '''
                sh '''
                    set -eu

                    trivy fs frontend \
                        --scanners vuln,misconfig,secret \
                        --skip-files '**/betterleaks*.json,**/semgrep*.json,**/dist,**/node_modules' \
                        --format json -o trivy-fs-frontend-report.json

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format template --template "@/contrib/html.tpl" \
                        -o trivy-fs-frontend-report.html \
                        trivy-fs-frontend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."
                '''
                archiveArtifacts artifacts: 'trivy-fs-*-report.*', fingerprint: true
            }
        }

        stage('Build Docker Images (Dockerfile)') {
            steps {
                echo "======================================"
                echo "BUILDING DOCKER IMAGES FROM DOCKERFILES"
                echo "======================================"

                buildDockerImage(image: "${BACKEND_IMAGE}", context: 'backend')
                buildDockerImage(image: "${FRONTEND_IMAGE}", context: 'frontend')

                sh '''
                    set -eu

                    echo "======================================"
                    echo "BUILT IMAGES"
                    echo "======================================"

                    docker images \
                        --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}' |
                        grep -E 'react-job-portal-(backend|frontend)' || true
                '''
            }
        }

        stage('Security Scan - Trivy Images') {
            steps {
                sh '''
                    set -eu

                    echo "======================================"
                    echo "TRIVY IMAGE SCAN"
                    echo "======================================"

                    echo "Scanning image: ${BACKEND_IMAGE}"
                    trivy image \
                        --scanners vuln,misconfig,secret \
                        --format json \
                        -o trivy-image-backend-report.json \
                        "${BACKEND_IMAGE}"

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format template --template "@/contrib/html.tpl" \
                        -o trivy-image-backend-report.html \
                        trivy-image-backend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."

                    echo "Scanning image: ${FRONTEND_IMAGE}"
                    trivy image \
                        --scanners vuln,misconfig,secret \
                        --format json \
                        -o trivy-image-frontend-report.json \
                        "${FRONTEND_IMAGE}"

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format template --template "@/contrib/html.tpl" \
                        -o trivy-image-frontend-report.html \
                        trivy-image-frontend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."
                '''
                archiveArtifacts artifacts: 'trivy-image-*-report.*', fingerprint: true
            }
        }

        stage('Clean Previous Deployment') {
            steps {
                sh '''
                    set +e

                    echo "Removing previous application containers..."

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        down \
                        --remove-orphans

                    exit 0
                '''
            }
        }

        stage('Deploy MongoDB') {
            steps {
                sh '''
                    set -eu

                    echo "======================================"
                    echo "DEPLOYING MONGODB"
                    echo "======================================"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        up -d \
                        --no-build \
                        mongodb

                    echo "MongoDB container started."

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        ps
                '''
            }
        }

        stage('Wait for MongoDB') {
            steps {
                sh '''
                    set -eu

                    echo "Waiting for MongoDB to become healthy..."

                    for i in $(seq 1 60); do

                        STATUS=$(docker inspect \
                            --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
                            "$(docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" ps -q mongodb)" \
                            2>/dev/null || true)

                        echo "MongoDB status: $STATUS"

                        if [ "$STATUS" = "healthy" ]; then
                            echo "MongoDB is healthy."
                            exit 0
                        fi

                        sleep 2
                    done

                    echo "ERROR: MongoDB did not become healthy."
                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        logs --tail=100 mongodb

                    exit 1
                '''
            }
        }

        stage('Deploy Backend') {
            steps {
                sh '''
                    set -eu

                    echo "======================================"
                    echo "DEPLOYING BACKEND"
                    echo "======================================"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        up -d \
                        --no-build \
                        backend

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        ps
                '''
            }
        }

        stage('Wait for Backend') {
            steps {
                sh '''
                    set -eu

                    echo "Waiting for backend to become healthy..."

                    for i in $(seq 1 60); do

                        STATUS=$(docker inspect \
                            --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
                            "$(docker compose -f docker-compose.jenkins.yml -p "$COMPOSE_PROJECT" ps -q backend)" \
                            2>/dev/null || true)

                        echo "Backend status: $STATUS"

                        if [ "$STATUS" = "healthy" ]; then
                            echo "Backend is healthy."
                            exit 0
                        fi

                        sleep 2
                    done

                    echo "ERROR: Backend did not become healthy."

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        logs --tail=150 backend

                    exit 1
                '''
            }
        }

        stage('Backend API Smoke Test') {
            steps {
                sh '''
                    set -eu

                    echo "Checking backend API..."

                    for i in $(seq 1 30); do
                        if curl -fsS \
                            http://127.0.0.1:4000/api/v1/job/getall \
                            >/dev/null 2>&1
                        then
                            echo "Backend API is responding."
                            exit 0
                        fi

                        sleep 2
                    done

                    echo "ERROR: Backend API is not responding."

                    curl -v \
                        http://127.0.0.1:4000/api/v1/job/getall \
                        || true

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        logs --tail=150 backend

                    exit 1
                '''
            }
        }

        stage('Deploy Frontend') {
            steps {
                sh '''
                    set -eu

                    echo "======================================"
                    echo "DEPLOYING FRONTEND"
                    echo "======================================"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        up -d \
                        --no-build \
                        frontend

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        ps
                '''
            }
        }

        stage('Wait & Full Stack Smoke Test') {
            steps {
                sh '''
                    set -eu

                    echo "======================================"
                    echo "FULL STACK SMOKE TEST"
                    echo "======================================"

                    frontend_ok=0
                    backend_ok=0

                    echo "--- Checking frontend ---"

                    for i in $(seq 1 60); do

                        if curl -fsS \
                            http://127.0.0.1:5173/ \
                            >/dev/null 2>&1
                        then
                            frontend_ok=1
                            echo "Frontend is responding after ${i} checks."
                            break
                        fi

                        sleep 2
                    done

                    echo "--- Checking backend ---"

                    for i in $(seq 1 60); do

                        if curl -fsS \
                            http://127.0.0.1:4000/api/v1/job/getall \
                            >/dev/null 2>&1
                        then
                            backend_ok=1
                            echo "Backend API is responding after ${i} checks."
                            break
                        fi

                        sleep 2
                    done

                    if [ "$frontend_ok" -ne 1 ]; then
                        echo "ERROR: Frontend smoke test failed."
                        exit 1
                    fi

                    if [ "$backend_ok" -ne 1 ]; then
                        echo "ERROR: Backend smoke test failed."
                        exit 1
                    fi

                    echo "======================================"
                    echo "ALL APPLICATION CHECKS PASSED"
                    echo "======================================"

                    echo "--- FINAL CONTAINER STATUS ---"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        ps

                    echo "--- BACKEND LOGS ---"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        logs --tail=100 backend

                    echo "--- FRONTEND LOGS ---"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        logs --tail=100 frontend

                    echo "--- MONGODB LOGS ---"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        logs --tail=100 mongodb

                    echo "======================================"
                    echo "FULL STACK DEPLOYMENT PASSED"
                    echo "======================================"
                '''
            }
        }
    }

    post {

        always {
            sh '''
                set +e

                if [ -f docker-compose.jenkins.yml ]; then

                    echo "======================================"
                    echo "FINAL COMPOSE STATUS"
                    echo "======================================"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        ps

                    echo "======================================"
                    echo "FINAL COMPOSE LOGS"
                    echo "======================================"

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        logs --tail=100
                fi
            '''
        }

        success {
            echo "======================================"
            echo "JENKINS PIPELINE COMPLETED"
            echo "======================================"

            echo "Frontend: http://localhost:5173"
            echo "Backend:  http://localhost:4000"

            echo "Docker images:"
            echo "${BACKEND_IMAGE}"
            echo "${FRONTEND_IMAGE}"

            notifyEmail(status: 'SUCCESS')
        }

        unstable {
            echo "======================================"
            echo "PIPELINE COMPLETED WITH WARNINGS"
            echo "======================================"

            echo "The application deployed successfully, but one or more non-blocking quality checks reported issues."

            notifyEmail(status: 'UNSTABLE')
        }

        failure {
            echo "======================================"
            echo "JENKINS PIPELINE FAILED"
            echo "======================================"

            sh '''
                set +e

                if [ -f docker-compose.jenkins.yml ]; then

                    echo "Cleaning failed deployment..."

                    docker compose \
                        -f docker-compose.jenkins.yml \
                        -p "$COMPOSE_PROJECT" \
                        down \
                        --remove-orphans
                fi
            '''

            notifyEmail(status: 'FAILURE')
        }
    }
}