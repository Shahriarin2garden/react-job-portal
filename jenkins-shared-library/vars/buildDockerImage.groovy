/**
 * Build a Docker image from the project Dockerfile.
 *
 * Parameters:
 *   image      (required) tag for the built image, e.g. env.BACKEND_IMAGE
 *   context    build context, e.g. 'backend' (default '.')
 *   dockerfile path to Dockerfile (default '<context>/Dockerfile')
 *   pull       run 'docker build --pull' (default true)
 *   buildArgs  list of 'KEY=value' build args (optional)
 *
 * Example:
 *   buildDockerImage(image: env.BACKEND_IMAGE, context: 'backend')
 */
def call(Map config = [:]) {
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
