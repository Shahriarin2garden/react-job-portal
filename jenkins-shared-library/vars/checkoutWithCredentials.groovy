/**
 * Checkout the application repository using an explicit private-repo credential.
 *
 * Required parameters (fall back to environment variables):
 *   url           -> GIT_URL
 *   branch        -> GIT_BRANCH
 *   credentialsId -> GIT_CREDENTIALS_ID
 *
 * Example:
 *   checkoutWithCredentials(
 *       url: 'https://github.com/Shahriarin2garden/react-job-portal.git',
 *       branch: 'main',
 *       credentialsId: 'github-private-repo-credentials'
 *   )
 */
def call(Map config = [:]) {
    def url = config.url ?: env.GIT_URL
    def branch = config.branch ?: env.GIT_BRANCH
    def credentialsId = config.credentialsId ?: env.GIT_CREDENTIALS_ID

    if (!url) {
        error 'checkoutWithCredentials: repository url is required (param url or env.GIT_URL)'
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
