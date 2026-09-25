import org.jobportal.Notify

/**
 * Send an HTML build notification through the Jenkins SMTP (Email Extension) config.
 *
 * Parameters:
 *   to        recipients (default env.CI_EMAIL_RECIPIENTS)
 *   status    SUCCESS / FAILURE / UNSTABLE (default currentBuild.currentResult)
 *   subject   optional custom subject
 *   attachLog attach build log (default true)
 *   changes   optional list of changes to include in the body
 *
 * Example:
 *   notifyEmail(status: 'SUCCESS')
 *   notifyEmail(status: 'FAILURE', attachLog: true)
 */
def call(Map config = [:]) {
    def status = config.status ?: (currentBuild.currentResult ?: 'UNKNOWN')
    def recipients = config.to ?: env.CI_EMAIL_RECIPIENTS

    if (!recipients) {
        echo 'notifyEmail: no recipients configured (env.CI_EMAIL_RECIPIENTS), skipping email.'
        return
    }

    def templateName = (status == 'SUCCESS') ? 'email/success.html' : 'email/failure.html'
    def template = libraryResource(templateName)

    def body = Notify.render(template, [
        PROJECT_NAME: env.PROJECT_NAME ?: env.JOB_NAME,
        JOB_NAME    : env.JOB_NAME,
        BUILD_NUMBER: env.BUILD_NUMBER,
        BUILD_URL   : env.BUILD_URL,
        STATUS      : status,
        GIT_BRANCH  : env.GIT_BRANCH ?: '',
        GIT_COMMIT  : env.GIT_COMMIT ?: '',
        CHANGES     : config.changes ?: 'See build console output for details.'
    ])

    emailext(
        subject: config.subject ?: "[${status}] ${env.JOB_NAME} #${env.BUILD_NUMBER}",
        body: body,
        to: recipients,
        mimeType: 'text/html',
        attachLog: (config.attachLog == null) ? true : config.attachLog
    )
}
