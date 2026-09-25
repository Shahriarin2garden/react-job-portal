pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
        skipDefaultCheckout(true)
        timeout(time: 60, unit: 'MINUTES')
    }

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
        FRONTEND_PORT   = '4173'

        VITE_API_URL    = 'http://192.168.56.50:4000/api/v1'
        FRONTEND_URL    = 'http://192.168.56.50:4173'
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
                script {
                    def url = env.GIT_URL
                    def branch = env.GIT_BRANCH
                    def credentialsId = env.GIT_CREDENTIALS_ID

                    if (!url) {
                        error 'Checkout: repository url is required'
                    }
                    if (!branch) {
                        branch = 'main'
                    }

                    def remote = [url: url]
                    if (credentialsId) {
                        remote.credentialsId = credentialsId
                    }

                    echo "Checking out ${url} (branch: ${branch}) with credential: ${credentialsId ?: '<none>'}"

                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${branch}"]],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [],
                        userRemoteConfigs: [remote]
                    ])
                }

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
                            -e VITE_API_URL="${VITE_API_URL}" \
                            -v "$PWD:/app" \
                            -w /app \
                            node:22-alpine \
                            sh -c "npm run build"

                        echo "Frontend build passed."
                    '''
                }
            }
        }

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

                            if ! command -v betterleaks &> /dev/null; then
                                echo "Installing betterleaks..."
                                npm install -g betterleaks || true
                            fi

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

                    archiveArtifacts artifacts: 'betterleaks-backend.json,betterleaks-frontend.json', fingerprint: true, allowEmptyArchive: true

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

                            if ! command -v semgrep &> /dev/null; then
                                echo "Installing semgrep..."
                                pip3 install semgrep || true
                            fi

                            semgrep scan --config auto backend --json --output=semgrep-backend.json
                            rc_backend=\$?

                            semgrep scan --config auto frontend --json --output=semgrep-frontend.json
                            rc_frontend=\$?

                            echo "semgrep exit codes -> backend: \$rc_backend, frontend: \$rc_frontend"

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

                    archiveArtifacts artifacts: 'semgrep-backend.json,semgrep-frontend.json', fingerprint: true, allowEmptyArchive: true

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

                    if ! command -v trivy &> /dev/null; then
                        echo "Installing trivy..."
                        curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin v0.58.1 || \
                        curl -sfL https://github.com/aquasecurity/trivy/releases/download/v0.58.1/trivy_0.58.1_Linux-64bit.tar.gz | tar -xz -C /usr/local/bin trivy || \
                        echo "WARNING: Trivy installation failed, assuming it's available in PATH"
                    fi

                    trivy fs backend \
                        --scanners vuln,misconfig \
                        --skip-files '**/betterleaks*.json,**/semgrep*.json' \
                        --format json -o trivy-fs-backend-report.json \
                        --skip-version-check

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format html \
                        -o trivy-fs-backend-report.html \
                        trivy-fs-backend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."
                '''
                sh '''
                    set -eu

                    trivy fs frontend \
                        --scanners vuln,misconfig \
                        --skip-files '**/betterleaks*.json,**/semgrep*.json,**/dist,**/node_modules' \
                        --format json -o trivy-fs-frontend-report.json \
                        --skip-version-check

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format html \
                        -o trivy-fs-frontend-report.html \
                        trivy-fs-frontend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."
                '''
                archiveArtifacts artifacts: 'trivy-fs-*-report.*', fingerprint: true, allowEmptyArchive: true
            }
        }

        stage('Build Docker Images') {
            steps {
                echo "======================================"
                echo "BUILDING DOCKER IMAGES FROM DOCKERFILES"
                echo "======================================"

                script {
                    def buildDockerImage = { Map config ->
                        def context = config.context ?: '.'
                        def image = config.image
                        if (!image) {
                            error 'buildDockerImage: image is required'
                        }
                        def dockerfile = config.dockerfile ?: "${context}/Dockerfile"
                        def pull = (config.pull == null) ? true : config.pull

                        def buildArgs = ''
                        if (config.buildArgs) {
                            config.buildArgs.each { arg ->
                                buildArgs += " --build-arg '${arg}'"
                            }
                        }

                        sh """
                            set -eu
                            echo "Building image '${image}' from '${dockerfile}' (context: '${context}')"
                            docker build ${pull ? '--pull' : ''}${buildArgs} -t '${image}' -f '${dockerfile}' '${context}'
                            docker image inspect '${image}' >/dev/null
                        """
                    }

                    buildDockerImage(image: "${BACKEND_IMAGE}", context: 'backend')
                    buildDockerImage(image: "${FRONTEND_IMAGE}", context: 'frontend', buildArgs: ["VITE_API_URL=${VITE_API_URL}"])
                }

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
                        --scanners vuln,misconfig \
                        --format json \
                        -o trivy-image-backend-report.json \
                        "${BACKEND_IMAGE}" \
                        --skip-version-check

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format html \
                        -o trivy-image-backend-report.html \
                        trivy-image-backend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."

                    echo "Scanning image: ${FRONTEND_IMAGE}"
                    trivy image \
                        --scanners vuln,misconfig \
                        --format json \
                        -o trivy-image-frontend-report.json \
                        "${FRONTEND_IMAGE}" \
                        --skip-version-check

                    echo "HTML report generation is best-effort:"
                    trivy convert \
                        --format html \
                        -o trivy-image-frontend-report.html \
                        trivy-image-frontend-report.json \
                        || echo "WARNING: could not generate HTML report; keep JSON report."
                '''
                archiveArtifacts artifacts: 'trivy-image-*-report.*', fingerprint: true, allowEmptyArchive: true
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
                            http://127.0.0.1:4173/ \
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

            echo "Frontend: http://localhost:4173"
            echo "Backend:  http://localhost:4000"

            echo "Docker images:"
            echo "${BACKEND_IMAGE}"
            echo "${FRONTEND_IMAGE}"

            script {
                notifyEmail(status: 'SUCCESS')
            }
        }

        unstable {
            echo "======================================"
            echo "PIPELINE COMPLETED WITH WARNINGS"
            echo "======================================"

            echo "The application deployed successfully, but one or more non-blocking quality checks reported issues."

            script {
                notifyEmail(status: 'UNSTABLE')
            }
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

            script {
                notifyEmail(status: 'FAILURE')
            }
        }
    }
}

// Inline shared library functions

def notifyEmail(Map config = [:]) {
    def status = config.status ?: (currentBuild.currentResult ?: 'UNKNOWN')
    def recipients = config.to ?: env.CI_EMAIL_RECIPIENTS

    if (!recipients || recipients == 'devops@example.com') {
        echo 'notifyEmail: no recipients configured (env.CI_EMAIL_RECIPIENTS), skipping email.'
        return
    }

    def templateName = (status == 'SUCCESS') ? 'success' : 'failure'
    def body = """
        <html>
        <body style="font-family: Arial, sans-serif; color: #333;">
            <div style="max-width: 640px; margin: 0 auto; border: 1px solid #ddd; border-radius: 8px; overflow: hidden;">
                <div style="background: ${status == 'SUCCESS' ? '#1f9d55' : '#cf1124'}; color: white; padding: 16px 24px;">
                    <h1 style="margin: 0; font-size: 20px;">Build ${status == 'SUCCESS' ? 'Succeeded' : 'Failed'}</h1>
                </div>
                <div style="padding: 24px;">
                    <p>The pipeline for <strong>${env.PROJECT_NAME ?: env.JOB_NAME}</strong> ${status == 'SUCCESS' ? 'completed successfully' : 'did not complete successfully'}.</p>
                    <table style="border-collapse: collapse; width: 100%;">
                        <tr><td style="padding: 6px 0; width: 150px; color: #666; font-weight: bold;">Job</td><td>${env.JOB_NAME}</td></tr>
                        <tr><td style="padding: 6px 0; width: 150px; color: #666; font-weight: bold;">Build</td><td>#${env.BUILD_NUMBER}</td></tr>
                        <tr><td style="padding: 6px 0; width: 150px; color: #666; font-weight: bold;">Branch</td><td>${env.GIT_BRANCH ?: ''}</td></tr>
                        <tr><td style="padding: 6px 0; width: 150px; color: #666; font-weight: bold;">Commit</td><td>${env.GIT_COMMIT ?: ''}</td></tr>
                        <tr><td style="padding: 6px 0; width: 150px; color: #666; font-weight: bold;">Status</td><td>${status}</td></tr>
                    </table>
                    <p><a href="${env.BUILD_URL}console">Open the build console</a></p>
                </div>
                <div style="padding: 12px 24px; background: #f5f5f5; color: #777; font-size: 12px;">Sent automatically by Jenkins</div>
            </div>
        </body>
        </html>
    """

    try {
        emailext(
            subject: config.subject ?: "[${status}] ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            body: body,
            to: recipients,
            mimeType: 'text/html',
            attachLog: (config.attachLog == null) ? true : config.attachLog
        )
        echo "Email sent to ${recipients}"
    } catch (e) {
        echo "Failed to send email: ${e.getMessage()}"
    }
}