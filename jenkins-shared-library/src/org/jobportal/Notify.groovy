package org.jobportal

/**
 * Small helpers shared by the react-job-portal pipeline steps.
 */
class Notify implements Serializable {

    /**
     * Replace ${TOKEN} placeholders in an email template.
     */
    static String render(String template, Map tokens) {
        def out = template ?: ''
        tokens.each { key, value ->
            out = out.replace('${' + key + '}', value == null ? '' : value.toString())
        }
        return out
    }
}
